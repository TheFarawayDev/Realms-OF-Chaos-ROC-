package com.thefarawaydev.realmsofchaos.ui;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;

/**
 * A small panel of words floating just over a block: what a rune stone, a puzzle tile, a cache, a drum or a dummy has to
 * say, said where it is rather than in the action bar. Dark, framed by a coloured rule above and below its message,
 * turned always to face whoever looks at it; it lasts a few seconds, and a new message on the same block replaces the
 * old one there.
 */
public final class BlockLabel {
    private record Shown(Display.TextDisplay display, long until) {}

    private static final Map<String, Shown> SHOWN = new HashMap<>();
    public static final String TAG = "realms_of_chaos_label";
    /** How long a panel stays up, and its background: dark, most of the way opaque. */
    public static final int STAY = 70, BACKGROUND = 0xD0141018;

    private BlockLabel() {}

    /**
     * A panel left behind in a saved world (the game stopped while it showed) is nobody's: when it loads again, it
     * goes. Called by each loader as an entity joins a level.
     */
    public static void onLoad(net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof Display.TextDisplay display) || !display.entityTags().contains(TAG)) return;
        for (Shown shown : SHOWN.values()) if (shown.display() == display) return;
        display.discard();
    }

    private static final Map<String, java.lang.reflect.Method> METHODS = new HashMap<>();

    /**
     * Calls one of a display's setters. They are not open to mods on every loader, so they are reached by name - the
     * game's own names, which it keeps.
     */
    private static void call(Class<?> owner, Object target, String name, Class<?> type, Object arg) {
        try {
            java.lang.reflect.Method m = METHODS.get(owner.getName() + "#" + name);
            if (m == null) {
                m = owner.getDeclaredMethod(name, type);
                m.setAccessible(true);
                METHODS.put(owner.getName() + "#" + name, m);
            }
            m.invoke(target, arg);
        } catch (ReflectiveOperationException | RuntimeException e) {
            com.mojang.logging.LogUtils.getLogger().warn("A block label could not set {}", name, e);
        }
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().identifier() + "|" + pos.asLong();
    }

    /** Shows a message over the block at {@code pos}, in a colour. @return the panel */
    public static Display.TextDisplay show(ServerLevel level, BlockPos pos, Component message, int colour) {
        return show(level, pos, message, colour, STAY);
    }

    public static Display.TextDisplay show(ServerLevel level, BlockPos pos, Component message, int colour, int ticks) {
        long now = level.getServer().getTickCount();
        String k = key(level, pos);
        Shown old = SHOWN.get(k);
        Display.TextDisplay display = old != null && !old.display().isRemoved() ? old.display() : null;
        if (display == null) {
            display = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
            if (display == null) return null;
            display.setPos(pos.getX() + 0.5, pos.getY() + 1.35, pos.getZ() + 0.5);
            call(Display.class, display, "setBillboardConstraints", Display.BillboardConstraints.class, Display.BillboardConstraints.CENTER);
            call(Display.TextDisplay.class, display, "setBackgroundColor", int.class, BACKGROUND);
            call(Display.TextDisplay.class, display, "setLineWidth", int.class, 240);
            call(Display.class, display, "setViewRange", float.class, 0.6F);
            display.addTag(TAG);
            SHOWN.put(k, new Shown(display, now + ticks));       // (known before it joins the level, so it is not taken for a stray)
            level.addFreshEntity(display);
        }
        call(Display.TextDisplay.class, display, "setText", Component.class, panel(message, colour));
        SHOWN.put(k, new Shown(display, now + ticks));
        return display;
    }

    /** The panel's words: a rule of the message's colour, the message, the rule again. */
    public static Component panel(Component message, int colour) {
        Style rule = Style.EMPTY.withColor(colour & 0xFFFFFF);
        MutableComponent out = Component.literal("◆ ─── ◆").withStyle(rule.withColor(dim(colour)));
        out.append(Component.literal("\n"));
        out.append(message.copy().withStyle(Style.EMPTY.withColor(colour & 0xFFFFFF)));
        out.append(Component.literal("\n"));
        out.append(Component.literal("◆ ─── ◆").withStyle(rule.withColor(dim(colour))));
        return out;
    }

    private static int dim(int c) {
        int r = (c >> 16 & 0xFF) * 3 / 5, g = (c >> 8 & 0xFF) * 3 / 5, b = (c & 0xFF) * 3 / 5;
        return r << 16 | g << 8 | b;
    }

    /** The message now over a block, or null. */
    public static Display.TextDisplay at(ServerLevel level, BlockPos pos) {
        Shown s = SHOWN.get(key(level, pos));
        return s == null || s.display().isRemoved() ? null : s.display();
    }

    /** Takes down the panels whose time is up. Run every tick. */
    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (Iterator<Shown> it = SHOWN.values().iterator(); it.hasNext(); ) {
            Shown s = it.next();
            if (s.display().isRemoved()) {
                it.remove();
            } else if (now >= s.until()) {
                s.display().discard();
                it.remove();
            }
        }
    }

    /** Takes them all down (a server stopping). */
    public static void clear() {
        for (Shown s : SHOWN.values()) s.display().discard();
        SHOWN.clear();
    }
}
