package com.thefarawaydev.realmsofchaos.world;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.server.MinecraftServer;

/**
 * Runs small pieces of work a few ticks later: the second half of a signature move, a hex that bursts, an echo.
 * Ticked once per server tick. Work queued on a server that has since stopped is dropped.
 */
public final class Scheduler {
    private record Task(MinecraftServer server, long due, Runnable work) {}

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final List<Task> TASKS = new ArrayList<>();

    private Scheduler() {}

    public static void after(MinecraftServer server, int ticks, Runnable work) {
        if (server == null) return;
        TASKS.add(new Task(server, server.getTickCount() + (long) Math.max(1, ticks), work));
    }

    public static int pending() {
        return TASKS.size();
    }

    public static void tick(MinecraftServer server) {
        if (TASKS.isEmpty()) return;
        long now = server.getTickCount();
        List<Task> due = new ArrayList<>();
        for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
            Task task = it.next();
            if (task.server() != server) {
                it.remove();
            } else if (task.due() <= now) {
                due.add(task);
                it.remove();
            }
        }
        for (Task task : due) {
            try {
                task.work().run();
            } catch (RuntimeException e) {
                LOGGER.warn("A delayed effect failed", e);
            }
        }
    }
}
