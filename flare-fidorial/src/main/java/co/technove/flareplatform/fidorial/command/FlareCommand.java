package co.technove.flareplatform.fidorial.command;

import co.technove.flare.exceptions.UserReportableException;
import co.technove.flare.internal.profiling.ProfileType;
import co.technove.flareplatform.fidorial.FlarePlatformFidorial;
import co.technove.flareplatform.fidorial.manager.ProfilingManager;
import fr.fidorial.command.CommandExecutor;
import fr.fidorial.command.CommandSender;
import fr.fidorial.entity.Player;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Locale;

/**
 * /flare profiler start [--cpu|--alloc|--lock|--wall|--ctimer]
 * /flare profiler stop
 * /flare profiler status
 */
public class FlareCommand implements CommandExecutor {

    private static final String PREFIX = "<dark_gray>[</dark_gray><bold><#6A7EDA>✈</color></bold><dark_gray>]</dark_gray> ";
    private static final String MAIN = "<#6A7EDA>";
    private static final String HEX = "<#E3EAEA>";
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        FlarePlatformFidorial platform = FlarePlatformFidorial.getInstance();
        if (platform == null) {

            sender.sendMessage(miniMessage.deserialize(PREFIX + "<red>Flare is not loaded.</red>"));
            return;
        }
        if (platform.config().commandConsoleOnly() && !sender.isConsole()) {
            sender.sendMessage(miniMessage.deserialize(PREFIX + "<red>This command can only be used from the console"
                + " (set command-console-only=false in plugins/flare/flare.properties to change this).</red>"));
            return;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("profiler")) {
            sendUsage(sender, label);
            return;
        }

        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "start" -> start(sender, args.length >= 3 ? args[2] : "--cpu");
            case "stop" -> stop(sender);
            case "status" -> status(sender);
            default -> sendUsage(sender, label);
        }
    }

    private void start(CommandSender sender, String rawType) {
        ProfileType type = switch (rawType.toLowerCase(Locale.ROOT)) {
            case "--alloc" -> ProfileType.ALLOC;
            case "--lock" -> ProfileType.LOCK;
            case "--wall" -> ProfileType.WALL;
            case "--ctimer" -> ProfileType.CTIMER;
            default -> ProfileType.ITIMER; // --cpu
        };

        if (ProfilingManager.isProfiling()) {
            sender.sendMessage(miniMessage.deserialize(PREFIX + "<red>A Flare is already running:</red> " + link(ProfilingManager.getProfilingUri())));
            return;
        }

        try {
            if (ProfilingManager.start(type)) {
                sender.sendMessage(miniMessage.deserialize(PREFIX + MAIN + "Flare started (" + type.name().toLowerCase(Locale.ROOT)
                    + "), it will run for 15 minutes unless stopped:</color> " + link(ProfilingManager.getProfilingUri())));
            } else {
                sender.sendMessage(miniMessage.deserialize(PREFIX + "<red>Flare is already running.</red>"));
            }
        } catch (UserReportableException e) {
            sender.sendMessage(miniMessage.deserialize(PREFIX + "<red>Flare failed to start: " + e.getUserError() + "</red>"));
            logger().warn("Error starting Flare", e);
        }
    }

    private void stop(CommandSender sender) {
        if (!ProfilingManager.isProfiling()) {
            sender.sendMessage(miniMessage.deserialize(PREFIX + "<red>Flare is not running.</red>"));
            return;
        }
        ProfilingManager.stop();
    }

    private void status(CommandSender sender) {
        if (!ProfilingManager.isProfiling()) {
            sender.sendMessage(miniMessage.deserialize(PREFIX + "<red>Flare is not running.</red>"));
            return;
        }
        long seconds = ProfilingManager.getTimeRan().toSeconds();
        sender.sendMessage(miniMessage.deserialize(PREFIX + MAIN + "Flare has been running for " + seconds + "s:</color> "
            + link(ProfilingManager.getProfilingUri())));
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(miniMessage.deserialize(PREFIX + MAIN + "Usage:</color> " + HEX
            + "/" + label + " profiler start [--cpu|--alloc|--lock|--wall|--ctimer] | stop | status</color>"));
    }

    private static String link(String uri) {
        return "<click:open_url:'" + uri + "'>" + HEX + uri + "</color></click>";
    }

    public static void broadcastStopped(String profilingUri) {
        String message = PREFIX + MAIN + "Profiling has been stopped.</color> " + link(profilingUri);
        broadcast(message);
        logger().info("Profiling has been stopped: {}", profilingUri);
    }

    public static void broadcastException() {
        String message = PREFIX + "<red>Profiling failed, check the console for details.</red>";
        broadcast(message);
    }

    private static void broadcast(String message) {
        FlarePlatformFidorial platform = FlarePlatformFidorial.getInstance();
        if (platform == null) {
            return;
        }
        for (Player player : platform.context().server().onlinePlayers()) {
            player.sendMessage(miniMessage.deserialize(message));
        }
    }

    private static org.slf4j.Logger logger() {
        FlarePlatformFidorial platform = FlarePlatformFidorial.getInstance();
        if (platform == null) {
            throw new IllegalStateException("Flare plugin is not loaded");
        }
        return platform.context().logger();
    }
}
