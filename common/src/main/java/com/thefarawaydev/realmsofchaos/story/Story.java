package com.thefarawaydev.realmsofchaos.story;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import com.thefarawaydev.realmsofchaos.world.Access;
import com.thefarawaydev.realmsofchaos.world.ProgressData;
import com.thefarawaydev.realmsofchaos.world.Scheduler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * The story of the mod, told in chapters by the Oracle. Every chapter is a book. Some are found - in the letter a
 * new player is given, in the chest of a Ruined Gate, in the shrines of the keystones - and all of them can be
 * taken from the Oracle's Chronicle (the Oracle's Flame in the hub, or the Codex) once they have been earned.
 */
public final class Story {
    public static final String AUTHOR = "The Oracle";

    private Story() {}

    public enum Chapter {
        PROLOGUE(null, 0), SUNDERING(null, 0), SKY(null, 0), SEA(null, 0), SHADE(null, 0), HUB(null, 0),
        ZEUS_FOOT(Line.ZEUS, 0), ZEUS_MID(Line.ZEUS, 5), ZEUS_HIGH(Line.ZEUS, 10), ZEUS_FALL(Line.ZEUS, 15),
        POSEIDON_FOOT(Line.POSEIDON, 0), POSEIDON_MID(Line.POSEIDON, 5), POSEIDON_HIGH(Line.POSEIDON, 10), POSEIDON_FALL(Line.POSEIDON, 15),
        HADES_FOOT(Line.HADES, 0), HADES_MID(Line.HADES, 5), HADES_HIGH(Line.HADES, 10), HADES_FALL(Line.HADES, 15),
        EPILOGUE(null, 45);

        /** The tower a chapter belongs to, if any. */
        public final Line line;
        /** How many gods (of that tower, or of all three for the epilogue) must be awake before it is written. */
        public final int needed;

        Chapter(Line line, int needed) {
            this.line = line;
            this.needed = needed;
        }

        public String id() {
            return name().toLowerCase();
        }

        private StoryData.Entry entry() {
            for (StoryData.Entry e : StoryData.ALL) if (e.id().equals(id())) return e;
            throw new IllegalStateException("No story text for " + id());
        }

        public int pages() {
            return entry().pages();
        }

        public String englishTitle() {
            return entry().title();
        }

        public Component title() {
            return Component.translatable("story." + RealmsOfChaos.MOD_ID + "." + id() + ".title");
        }

        public Component blurb() {
            return Component.translatable("story." + RealmsOfChaos.MOD_ID + "." + id() + ".blurb");
        }

        /** Has this player earned the chapter? The letter is everyone's; the rest follow the journey. */
        public boolean unlocked(ServerPlayer player) {
            if (this == PROLOGUE) return true;
            MinecraftServer server = player.level().getServer();
            if (server == null) return false;
            ProgressData data = ProgressData.get(server);
            if (this == EPILOGUE) return data.total(player.getUUID()) >= needed;
            if (needed == 0) return Access.attuned(player);
            return data.count(player.getUUID(), line) >= needed;
        }

        /** The chapter as a written book. */
        public ItemStack book() {
            StoryData.Entry entry = entry();
            List<Filterable<Component>> pages = new ArrayList<>();
            for (int n = 1; n <= entry.pages(); n++) {
                pages.add(Filterable.passThrough(Component.translatable("story." + RealmsOfChaos.MOD_ID + "." + id() + ".page" + n)));
            }
            ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
            stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(entry.title()), AUTHOR, 0, pages, true));
            return stack;
        }

        public boolean isBook(ItemStack stack) {
            WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
            return content != null && AUTHOR.equals(content.author()) && entry().title().equals(content.title().raw());
        }
    }

    public static int unlockedCount(ServerPlayer player) {
        int n = 0;
        for (Chapter c : Chapter.values()) if (c.unlocked(player)) n++;
        return n;
    }

    public static boolean holds(ServerPlayer player, Chapter chapter) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (chapter.isBook(player.getInventory().getItem(i))) return true;
        }
        return false;
    }

    /** Hands the player a chapter's book, unless they are already carrying it. @return true if it was given. */
    public static boolean give(ServerPlayer player, Chapter chapter) {
        if (holds(player, chapter)) return false;
        ItemStack book = chapter.book();
        if (!player.getInventory().add(book) && !book.isEmpty()) player.drop(book, false, net.minecraft.util.Prediction.SERVER_ONLY);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    /** The chapters a victory over {@code god} has just written for this player, in order. */
    public static List<Chapter> writtenBy(ServerPlayer player, God god) {
        List<Chapter> out = new ArrayList<>();
        MinecraftServer server = player.level().getServer();
        if (server == null) return out;
        ProgressData data = ProgressData.get(server);
        int count = data.count(player.getUUID(), god.line());
        for (Chapter c : Chapter.values()) {
            if (c.line == god.line() && c.needed > 0 && c.needed == count) out.add(c);
        }
        if (data.total(player.getUUID()) == Chapter.EPILOGUE.needed && god.isFinalBoss()) out.add(Chapter.EPILOGUE);
        return out;
    }

    /**
     * Called when a god falls to a player for the first time. New chapters are announced a few seconds later, once
     * the victory has had its moment; a king's own words, and the epilogue, are handed over as books.
     */
    public static void onDefeated(ServerPlayer player, God god) {
        List<Chapter> written = writtenBy(player, god);
        if (written.isEmpty()) return;
        MinecraftServer server = player.level().getServer();
        Runnable tell = () -> {
            if (!player.isAlive() || player.hasDisconnected()) return;
            for (Chapter c : written) {
                boolean given = (c.needed == 15 || c == Chapter.EPILOGUE) && give(player, c);
                Hud.actionBar(player, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + (given ? ".story.given" : ".story.new"), c.title()), 0xF5B82E));
            }
        };
        Scheduler.after(server, 100, tell);
    }
}
