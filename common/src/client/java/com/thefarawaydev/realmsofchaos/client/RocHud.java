package com.thefarawaydev.realmsofchaos.client;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.ui.HudFeed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

/**
 * The mod's own HUD, drawn by the player's game from the mod's textures: no fonts of pictures, no boss bars
 * borrowed and painted over.
 *
 * <p>At the top of the screen there is one thing at a time. In a trial it is the god's panel: its tower's frame
 * (plain, crystal under an aegis, toothed in its wrath), its health, marks where the aegis rises and the wrath
 * begins, a gem for each phase reached, the pylons still standing or the rings of floor still left, and - for those
 * who have switched it on - the move it is winding up. Otherwise it is the plaque of the floor or passage. Under
 * that, a card when there is one to read; and above the hotbar, the combat meter.
 *
 * <p>What a god is doing is read from the god; the rest from the lines {@link HudFeed} sends.
 */
public final class RocHud {
    private static final int FRAME_W = 200, FRAME_H = 22, BAR_X = 9, BAR_W = 182, CARD_W = 220, CARD_H = 46;
    private static final String[] CARDS = {"tip", "insight", "notice", "aegis", "wrath", "boon"};
    private static final int[] CARD_INK = {0xFF55E6F5, 0xFFFFAA3C, 0xFFECD096, 0xFF7EEBFF, 0xFFFF5A3C, 0xFF78E68C};
    private static final Identifier ICONS = hud("icons"), OVER[] = {hud("over_1"), hud("over_2"), hud("over_3")}, PLAQUE = hud("frame_cast");
    /** Icons in the sheet, as tools/hud_art.py lays them out. */
    private static final int PIP_OFF = 0, PIP_ON = 1, PARRY_READY = 2, PARRY_SPENT = 3, RIPOSTE = 4, PYLON = 5, PYLON_GONE = 6, RING = 7, RING_GONE = 8, ICON_COUNT = 9;

    private static boolean broken;
    private static int castingId = -1;
    private static long castingSince;

    private RocHud() {}

    private static Identifier hud(String name) {
        return RealmsOfChaos.id("textures/gui/hud/" + name + ".png");
    }

    /** Called once a frame by each loader's HUD hook. A fault here is logged once and the HUD left off: it never takes the game down. */
    public static void draw(GuiGraphicsExtractor graphics) {
        if (broken) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            paint(graphics, mc);
        } catch (Throwable fault) {
            broken = true;
            org.slf4j.LoggerFactory.getLogger(RealmsOfChaos.MOD_ID).error("The Realms of Chaos HUD could not be drawn and has been turned off", fault);
        }
    }

    private static void paint(GuiGraphicsExtractor g, Minecraft mc) {
        Font font = mc.font;
        int width = g.guiWidth(), height = g.guiHeight(), middle = width / 2;
        Scoreboard board = mc.level.getScoreboard();
        Objective feed = board.getObjective(HudFeed.OBJECTIVE);
        int options = number(board, feed, HudFeed.OPTIONS, HudFeed.OPT_COMBAT | HudFeed.OPT_ART);
        // a game and a world running different builds of the mod: say so, where it cannot be missed
        if (feed != null && board.getPlayerScoreInfo(ScoreHolder.forNameOnly(HudFeed.BUILD), feed) != null) {
            String theirs = text(board, feed, HudFeed.BUILD).getString();
            if (!theirs.isEmpty() && !theirs.equals(RealmsOfChaos.version())) {
                Component warn = Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".build.differs", RealmsOfChaos.version(), theirs);
                int w = font.width(warn);
                g.fill(middle - w / 2 - 4, height - 78, middle + w / 2 + 4, height - 66, 0xD0400000);
                g.centeredText(font, warn, middle, height - 76, 0xFFFFD0D0);
            }
        }
        // with the drawn interface switched off the game's own bars and text are used: nothing is drawn over them
        if ((options & HudFeed.OPT_ART) == 0) return;

        int top = 3;
        GodBoss god = nearestTrial(mc);
        if (god != null) {
            top = boss(g, font, middle, top, god, mc.level.getGameTime(), (options & HudFeed.OPT_CASTS) != 0);
        } else {
            castingId = -1;
            if (feed != null && board.getPlayerScoreInfo(ScoreHolder.forNameOnly(HudFeed.FLOOR), feed) != null) {
                top = plaque(g, font, middle, top, number(board, feed, HudFeed.FLOOR, 0), text(board, feed, HudFeed.FLOOR));
                if (board.getPlayerScoreInfo(ScoreHolder.forNameOnly(HudFeed.ILL), feed) != null) {
                    // what weighs on this level: in its own colour, or greyed while a shrine holds it off
                    boolean held = (number(board, feed, HudFeed.ILL, 0) & HudFeed.ILL_HELD) != 0;
                    Component ill = text(board, feed, HudFeed.ILL);
                    int w = font.width(ill);
                    g.fill(middle - w / 2 - 4, top, middle + w / 2 + 4, top + 11, 0xC0170B22);
                    g.fill(middle - w / 2 - 4, top + 10, middle + w / 2 + 4, top + 11, held ? 0xFF6E6A78 : 0xFFB45CFF);
                    g.centeredText(font, ill, middle, top + 1, held ? 0xFF9A96A6 : 0xFFD9A8FF);
                    top += 12;
                }
            }
        }
        if (feed != null && board.getPlayerScoreInfo(ScoreHolder.forNameOnly(HudFeed.CARD), feed) != null) {
            card(g, font, middle, top + 2, number(board, feed, HudFeed.CARD, 0), text(board, feed, HudFeed.CARD));
        }
        if (feed != null && (options & HudFeed.OPT_COMBAT) != 0 && board.getPlayerScoreInfo(ScoreHolder.forNameOnly(HudFeed.METER), feed) != null) {
            meter(g, middle, height - 62, number(board, feed, HudFeed.METER, 0));
        }
    }

    private static int number(Scoreboard board, Objective feed, String line, int otherwise) {
        if (feed == null) return otherwise;
        ReadOnlyScoreInfo info = board.getPlayerScoreInfo(ScoreHolder.forNameOnly(line), feed);
        return info == null ? otherwise : info.value();
    }

    private static Component text(Scoreboard board, Objective feed, String line) {
        ReadOnlyScoreInfo info = board.getPlayerScoreInfo(ScoreHolder.forNameOnly(line), feed);
        return info instanceof Score score && score.display() != null ? score.display() : Component.empty();
    }

    /** The god whose trial the player is in: the nearest one, alive, that is not a statue. */
    private static GodBoss nearestTrial(Minecraft mc) {
        GodBoss nearest = null;
        double best = 56.0 * 56.0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof GodBoss boss) || !boss.isAlive() || !boss.inTrial()) continue;
            double d = boss.distanceToSqr(mc.player);
            if (d < best) {
                best = d;
                nearest = boss;
            }
        }
        return nearest;
    }

    private static void icon(GuiGraphicsExtractor g, int icon, int x, int y) {
        g.blit(RenderPipelines.GUI_TEXTURED, ICONS, x, y, icon * 8.0F, 0.0F, 8, 8, ICON_COUNT * 8, 8);
    }

    /** A bar's fill: the colour, a lighter line along its top and a darker one along its bottom. */
    private static void fill(GuiGraphicsExtractor g, int x, int y, int width, int colour) {
        if (width <= 0) return;
        g.fill(x, y, x + width, y + 5, colour);
        g.fill(x, y, x + width, y + 1, blend(colour, 0xFFFFFFFF, 0.35F));
        g.fill(x, y + 4, x + width, y + 5, blend(colour, 0xFF000000, 0.35F));
    }

    private static int blend(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) + ((((b >> 16) & 255) - ((a >> 16) & 255)) * t));
        int gr = (int) (((a >> 8) & 255) + ((((b >> 8) & 255) - ((a >> 8) & 255)) * t));
        int bl = (int) ((a & 255) + (((b & 255) - (a & 255)) * t));
        return 0xFF000000 | (r << 16) | (gr << 8) | bl;
    }

    /** The god's panel. @return where the next thing below it may start */
    private static int boss(GuiGraphicsExtractor g, Font font, int middle, int top, GodBoss boss, long now, boolean casts) {
        God god = boss.god();
        Line line = god.line();
        int phase = Math.max(1, Math.min(3, boss.phase())), x = middle - FRAME_W / 2;
        boolean shielded = boss.hudShielded();
        String frame = "frame_" + line.id() + (shielded ? "_aegis" : phase >= 3 ? "_wrath" : "");
        g.blit(RenderPipelines.GUI_TEXTURED, hud(frame), x, top, 0.0F, 0.0F, FRAME_W, FRAME_H, FRAME_W, FRAME_H);
        float health = boss.getMaxHealth() <= 0.0F ? 0.0F : Math.max(0.0F, Math.min(1.0F, boss.getHealth() / boss.getMaxHealth()));
        int colour = shielded ? 0xFF7EEBFF : phase >= 3 ? 0xFFFF5A3C : 0xFF000000 | line.color();
        fill(g, x + BAR_X, top + 13, health <= 0.0F ? 0 : Math.max(1, Math.round(health * BAR_W)), colour);
        g.blit(RenderPipelines.GUI_TEXTURED, OVER[phase - 1], x, top, 0.0F, 0.0F, FRAME_W, FRAME_H, FRAME_W, FRAME_H);
        g.centeredText(font, god.displayName(), middle, top + 2, 0xFFF5E6C8);
        // what the phase counts, at the left of the plaque: pylons under an aegis, rings of floor in the wrath
        int all = boss.hudCountAll(), lit = boss.hudCountLit();
        for (int i = 0; i < all && i < 5; i++) icon(g, shielded ? (i < lit ? PYLON : PYLON_GONE) : (i < lit ? RING : RING_GONE), x + 37 + i * 7, top + 2);
        int below = top + FRAME_H + 1;
        // the move it is winding up, for those who have asked to see it: a second, thinner bar on the same panel
        if (boss.isCasting()) {
            if (castingId != boss.getId()) {
                castingId = boss.getId();
                castingSince = now;
            }
            if (casts) {
                float wound = Math.max(0.0F, Math.min(1.0F, (now - castingSince) / (float) GodBoss.WINDUP));
                int cx = middle - 60;
                g.fill(cx - 1, below, cx + 121, below + 12, 0xFF05040B);
                g.fill(cx, below + 1, cx + 120, below + 11, 0xFF18142C);
                g.fill(cx, below + 1, cx + Math.round(120 * wound), below + 11, 0xFF7A5A1E);
                Component move = boss.hudCastKind() == 2 ? Component.translatable("cast." + RealmsOfChaos.MOD_ID + "." + line.id()) : god.moveName();
                g.centeredText(font, move, middle, below + 2, 0xFFFFE08A);
                below += 14;
            }
        } else if (castingId == boss.getId()) {
            castingId = -1;
        }
        return below;
    }

    /** The plaque of the floor or passage: what it is, and how far along. */
    private static int plaque(GuiGraphicsExtractor g, Font font, int middle, int top, int value, Component words) {
        int x = middle - FRAME_W / 2, line = value & 3, done = (value >> 4) & 255, of = (value >> 12) & 255;
        g.blit(RenderPipelines.GUI_TEXTURED, PLAQUE, x, top, 0.0F, 0.0F, FRAME_W, FRAME_H, FRAME_W, FRAME_H);
        int colour = 0xFF000000 | Line.values()[Math.min(line, Line.values().length - 1)].color();
        fill(g, x + BAR_X, top + 13, of <= 0 ? 0 : Math.round(BAR_W * Math.min(1.0F, done / (float) of)), colour);
        for (int i = 1; i < of && of <= 20; i++) {
            int nx = x + BAR_X + Math.round(BAR_W * i / (float) of);
            g.fill(nx, top + 13, nx + 1, top + 18, 0xAA05040B);
        }
        // the first part of the words on the plaque itself, whatever follows on a strip under it
        String all = words.getString();
        int cut = all.indexOf(" - ");
        String head = font.width(all) <= 126 || cut < 0 ? all : all.substring(0, cut), rest = head.length() == all.length() ? "" : all.substring(cut + 3);
        if (font.width(head) <= 126) g.centeredText(font, head, middle, top + 2, 0xFFF5E6C8);
        else rest = all;
        if (rest.isEmpty()) return top + FRAME_H + 1;
        int w = font.width(rest);
        g.fill(middle - w / 2 - 4, top + FRAME_H, middle + w / 2 + 4, top + FRAME_H + 11, 0xC005040B);
        g.centeredText(font, rest, middle, top + FRAME_H + 2, 0xFFD8CBB0);
        return top + FRAME_H + 12;
    }

    /** A card: its artwork, and its lines - the first in the card's colour when it is a heading. */
    private static void card(GuiGraphicsExtractor g, Font font, int middle, int top, int value, Component words) {
        int kind = Math.max(0, Math.min(CARDS.length - 1, value & 15));
        boolean headed = (value & 16) != 0;
        int x = middle - CARD_W / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, hud("card_" + CARDS[kind]), x, top, 0.0F, 0.0F, CARD_W, CARD_H, CARD_W, CARD_H);
        String[] lines = words.getString().split("\n");
        int count = Math.min(3, lines.length), first = top + 14 + (3 - count) * 5;
        for (int i = 0; i < count; i++) {
            g.centeredText(font, lines[i], middle, first + i * 10, headed && i == 0 ? CARD_INK[kind] : 0xFFFFFFFF);
        }
    }

    /** The combat meter: a pip for each point of momentum, the parry mark, and a blade while a riposte waits. */
    private static void meter(GuiGraphicsExtractor g, int middle, int y, int value) {
        int momentum = value & 15, x = middle - 26;
        for (int i = 0; i < 5; i++) icon(g, i < momentum ? PIP_ON : PIP_OFF, x + i * 7, y);
        icon(g, (value & 16) != 0 ? PARRY_READY : PARRY_SPENT, x + 39, y);
        if ((value & 32) != 0) icon(g, RIPOSTE, x + 49, y);
    }
}
