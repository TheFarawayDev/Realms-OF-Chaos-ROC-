import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Gods;
import com.thefarawaydev.realmsofchaos.world.Realm;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.dimension.DimensionType;

/** Offline sanity check of Realms of Chaos content (no server / EULA needed). */
public class Validate {
    static int failures = 0;

    static void check(boolean ok, String msg) {
        if (!ok) { failures++; System.out.println("FAIL: " + msg); }
    }

    static void unfreeze(Registry<?> reg) throws Exception {
        var frozen = net.minecraft.core.MappedRegistry.class.getDeclaredField("frozen");
        frozen.setAccessible(true);
        frozen.set(reg, false);
        var intr = net.minecraft.core.MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
        intr.setAccessible(true);
        intr.set(reg, new java.util.IdentityHashMap<>());
    }

    public static void main(String[] args) throws Exception {
        Path res = Path.of(args[0]);
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        // 1. roster sanity
        check(Gods.ALL.size() == 45, "45 gods, got " + Gods.ALL.size());
        Set<String> ids = new HashSet<>();
        for (God g : Gods.ALL) {
            check(ids.add(g.id()), "duplicate id " + g.id());
            check(Gods.get(g.line(), g.rank()) == g, "rank lookup " + g.id());
        }

        // 2. construct + register every block / item / entity type exactly as the loaders do
        unfreeze(BuiltInRegistries.BLOCK);
        unfreeze(BuiltInRegistries.ITEM);
        unfreeze(BuiltInRegistries.ENTITY_TYPE);
        for (ModContent.Def<Block> d : ModContent.BLOCK_DEFS) {
            var id = RealmsOfChaos.id(d.id());
            Block b = d.factory().apply(ResourceKey.create(Registries.BLOCK, id));
            Registry.register(BuiltInRegistries.BLOCK, id, b);
            d.ref().bind(() -> b);
        }
        int items = 0;
        for (ModContent.Def<Item> d : ModContent.ITEM_DEFS) {
            var key = ResourceKey.create(Registries.ITEM, RealmsOfChaos.id(d.id()));
            Item it = d.factory().apply(key);
            Registry.register(BuiltInRegistries.ITEM, RealmsOfChaos.id(d.id()), it);
            d.ref().bind(() -> it);
            items++;
            check(it.getDescriptionId().length() > 0, "description id " + d.id());
        }
        EntityType<?> boss = ModContent.createGodBossType(ModContent.godBossKey());
        Registry.register(BuiltInRegistries.ENTITY_TYPE, RealmsOfChaos.id("god_boss"), boss);
        @SuppressWarnings("unchecked") EntityType<com.thefarawaydev.realmsofchaos.entity.GodBoss> bt = (EntityType<com.thefarawaydev.realmsofchaos.entity.GodBoss>) boss;
        ModContent.GOD_BOSS.bind(() -> bt);
        check(boss != null, "boss type");
        check(ModContent.tabItems().size() == 3 + 45 + 12 + 6 + 1 + 22 + 4 + 21, "tab item count " + ModContent.tabItems().size());

        // 3. dimension types + dimensions parse with vanilla codecs
        HolderLookup.Provider lookup = VanillaRegistries.createWorldLookup();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, lookup);
        for (Realm r : Realm.values()) {
            Path dt = res.resolve("data/realms_of_chaos/dimension_type/" + r.id() + ".json");
            JsonElement je = JsonParser.parseString(Files.readString(dt));
            var res1 = DimensionType.DIRECT_CODEC.parse(ops, je);
            check(res1.result().isPresent(), "dimension_type " + r.id() + ": " + res1.error().map(e -> e.message()).orElse("?"));
            check(Files.exists(res.resolve("data/realms_of_chaos/dimension/" + r.id() + ".json")), "dimension " + r.id());
        }

        // 3b. pack.mcmeta must be accepted as both a resource pack and a data pack
        JsonElement mcmeta = JsonParser.parseString(Files.readString(res.resolve("pack.mcmeta"))).getAsJsonObject().get("pack");
        for (var type : new net.minecraft.server.packs.PackType[]{net.minecraft.server.packs.PackType.CLIENT_RESOURCES, net.minecraft.server.packs.PackType.SERVER_DATA}) {
            var codec = net.minecraft.server.packs.metadata.pack.PackMetadataSection.forPackType(type).codec();
            var parsed = codec.parse(JsonOps.INSTANCE, mcmeta);
            check(parsed.result().isPresent(), "pack.mcmeta as " + type + ": " + parsed.error().map(e -> e.message()).orElse("?"));
            parsed.result().ifPresent(p -> System.out.println("pack.mcmeta " + type + " formats=" + p.supportedFormats()));
        }

        // 4. assets exist for every item / block / god
        for (God g : Gods.ALL) {
            for (String p : new String[]{
                    "assets/realms_of_chaos/items/" + g.id() + "_weapon.json",
                    "assets/realms_of_chaos/models/item/" + g.id() + "_weapon.json",
                    "assets/realms_of_chaos/textures/item/" + g.id() + "_weapon.png",
                    "assets/realms_of_chaos/textures/entity/god/" + g.id() + ".png"}) {
                check(Files.exists(res.resolve(p)), "missing " + p);
            }
        }
        for (ModContent.Def<Block> d : ModContent.BLOCK_DEFS) {
            for (String p : new String[]{
                    "assets/realms_of_chaos/blockstates/" + d.id() + ".json",
                    "assets/realms_of_chaos/models/block/" + d.id() + ".json",
                    "assets/realms_of_chaos/items/" + d.id() + ".json"}) {
                check(Files.exists(res.resolve(p)), "missing " + p);
            }
        }

        // 5. lang keys
        String lang = Files.readString(res.resolve("assets/realms_of_chaos/lang/en_us.json"));
        for (God g : Gods.ALL) {
            check(lang.contains("\"god.realms_of_chaos." + g.id() + "\""), "lang name " + g.id());
            check(lang.contains("\"item.realms_of_chaos." + g.id() + "_weapon\""), "lang weapon " + g.id());
        }
        for (ModContent.Def<Block> d : ModContent.BLOCK_DEFS) {
            check(lang.contains("\"block.realms_of_chaos." + d.id() + "\""), "lang block " + d.id());
        }
        for (God g : Gods.ALL) {
            check(lang.contains("\"move.realms_of_chaos." + g.id() + "\""), "lang move " + g.id());
        }
        for (com.thefarawaydev.realmsofchaos.god.Signature kind : com.thefarawaydev.realmsofchaos.god.Signature.values()) {
            check(lang.contains("\"move.realms_of_chaos.kind." + kind.name().toLowerCase() + "\""), "lang move kind " + kind);
        }

        for (com.thefarawaydev.realmsofchaos.story.Story.Chapter chapter : com.thefarawaydev.realmsofchaos.story.Story.Chapter.values()) {
            String base = "\"story.realms_of_chaos." + chapter.id();
            check(lang.contains(base + ".title\"") && lang.contains(base + ".blurb\""), "lang story " + chapter.id());
            for (int n = 1; n <= chapter.pages(); n++) check(lang.contains(base + ".page" + n + "\""), "lang story " + chapter.id() + " page " + n);
        }
        for (com.thefarawaydev.realmsofchaos.god.Line line : com.thefarawaydev.realmsofchaos.god.Line.values()) {
            check(lang.contains("\"item.realms_of_chaos.keystone_" + line.id() + "\"") && lang.contains("\"item.realms_of_chaos.seeker_" + line.id() + "\"")
                    && lang.contains("\"ruin.realms_of_chaos." + line.id() + "\""), "lang keystone/seeker/shrine " + line.id());
        }

        for (com.thefarawaydev.realmsofchaos.entity.Sentinel.Kind kind : com.thefarawaydev.realmsofchaos.entity.Sentinel.Kind.values()) {
            check(Files.exists(res.resolve("assets/realms_of_chaos/textures/entity/sentinel/" + kind.id() + ".png")), "missing sentinel skin " + kind.id());
            check(lang.contains("\"entity.realms_of_chaos.sentinel." + kind.id() + "\""), "lang sentinel " + kind.id());
        }

        for (com.thefarawaydev.realmsofchaos.ui.HandbookData.Topic topic : com.thefarawaydev.realmsofchaos.ui.HandbookData.ALL) {
            check(lang.contains("\"handbook.realms_of_chaos." + topic.id() + ".title\""), "lang handbook " + topic.id());
            for (int n = 1; n <= topic.lines(); n++) check(lang.contains("\"handbook.realms_of_chaos." + topic.id() + ".line" + n + "\""), "lang handbook " + topic.id() + " line " + n);
        }
        for (com.thefarawaydev.realmsofchaos.ui.Tutorial.Tip tip : com.thefarawaydev.realmsofchaos.ui.Tutorial.Tip.values()) {
            check(lang.contains("\"tip.realms_of_chaos." + tip.id() + "\""), "lang tip " + tip.id());
        }

        // 5b. relic tags: one per tower with its fifteen weapons
        for (com.thefarawaydev.realmsofchaos.god.Line line : com.thefarawaydev.realmsofchaos.god.Line.values()) {
            Path tag = res.resolve("data/realms_of_chaos/tags/item/relics/" + line.id() + ".json");
            check(Files.exists(tag), "missing relic tag " + line.id());
            if (!Files.exists(tag)) continue;
            String listed = Files.readString(tag);
            for (God g : Gods.ALL) {
                check(listed.contains("\"realms_of_chaos:" + g.id() + "_weapon\"") == (g.line() == line), "relic tag " + line.id() + " and " + g.id());
            }
        }
        check(Files.exists(res.resolve("data/realms_of_chaos/tags/item/relics.json")), "missing relic tag of all relics");

        // 6. block models load with the game's own model reader, and every texture they name exists
        for (ModContent.Def<Block> d : ModContent.BLOCK_DEFS) {
            Path model = res.resolve("assets/realms_of_chaos/models/block/" + d.id() + ".json");
            try (var reader = Files.newBufferedReader(model)) {
                net.minecraft.client.resources.model.cuboid.CuboidModel.fromStream(reader);
            } catch (RuntimeException e) {
                check(false, "block model " + d.id() + ": " + e);
            }
            var textures = JsonParser.parseString(Files.readString(model)).getAsJsonObject().getAsJsonObject("textures");
            for (var entry : textures.entrySet()) {
                String ref = entry.getValue().getAsString();
                if (!ref.startsWith("realms_of_chaos:")) continue;
                check(Files.exists(res.resolve("assets/realms_of_chaos/textures/" + ref.substring("realms_of_chaos:".length()) + ".png")),
                        "block model " + d.id() + " names a missing texture " + ref);
            }
        }

        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " FAILURE(S)");
        System.exit(failures == 0 ? 0 : 1);
    }
}
