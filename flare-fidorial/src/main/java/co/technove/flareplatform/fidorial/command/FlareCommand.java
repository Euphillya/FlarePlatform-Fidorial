package co.technove.flareplatform.fidorial.command;

import co.technove.flare.exceptions.UserReportableException;
import co.technove.flare.internal.profiling.ProfileType;
import co.technove.flareplatform.fidorial.FlarePlatformFidorial;
import co.technove.flareplatform.fidorial.manager.ProfilingManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fr.fidorial.command.CommandSender;
import fr.fidorial.command.CommandSource;
import fr.fidorial.entity.Player;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.minimessage.MiniMessage;

import static fr.fidorial.command.Commands.literal;

/**
 * /flare profiler start [--cpu|--alloc|--lock|--wall|--ctimer]
 * /flare profiler stop
 * /flare profiler status
 */
public final class FlareCommand {

    public static final String COMMAND_NAME = "flare";
    public static final String PERMISSION = "flare.use";

    private static final String PREFIX = "<dark_gray>[</dark_gray><bold><#6A7EDA>✈</color></bold><dark_gray>]</dark_gray> ";
    private static final String MAIN = "<#6A7EDA>";
    private static final String HEX = "<#E3EAEA>";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final List<Map.Entry<String, ProfileType>> PROFILE_TYPES = List.of(
        Map.entry("--cpu", ProfileType.ITIMER),
        Map.entry("--alloc", ProfileType.ALLOC),
        Map.entry("--lock", ProfileType.LOCK),
        Map.entry("--wall", ProfileType.WALL),
        Map.entry("--ctimer", ProfileType.CTIMER)
    );

    private FlareCommand() {
    }

    public static LiteralCommandNode<CommandSource> create() {
        final LiteralArgumentBuilder<CommandSource> start = literal("start")
            .executes(ctx -> start(ctx, ProfileType.ITIMER));

        for (final Map.Entry<String, ProfileType> entry : PROFILE_TYPES) {
            start.then(literal(entry.getKey()).executes(ctx -> start(ctx, entry.getValue())));
        }

        return literal(COMMAND_NAME)
            .requires(FlareCommand::canUse)
            .executes(FlareCommand::usage)
            .then(literal("profiler")
                .executes(FlareCommand::usage)
                .then(start)
                .then(literal("stop").executes(FlareCommand::stop))
                .then(literal("status").executes(FlareCommand::status)))
            .build();
    }


    private static boolean canUse(final CommandSource source) {
        final FlarePlatformFidorial platform = FlarePlatformFidorial.getInstance();
        if (platform == null) {
            return false;
        }

        final CommandSender sender = source.sender();

        if (platform.config().commandConsoleOnly() && sender instanceof Player) {
            return false;
        }

        return sender.isOperator() || sender.hasPermission(PERMISSION);
    }

    private static int start(final CommandContext<CommandSource> ctx, final ProfileType type) {
        final CommandSender sender = ctx.getSource().sender();

        if (ProfilingManager.isProfiling()) {
            send(sender, PREFIX + "<red>A Flare is already running:</red> " + link(ProfilingManager.getProfilingUri()));
            return 0;
        }

        try {
            if (ProfilingManager.start(type)) {
                send(sender, PREFIX + MAIN + "Flare started (" + type.name().toLowerCase(Locale.ROOT)
                    + "), it will run for 15 minutes unless stopped:</color> "
                    + link(ProfilingManager.getProfilingUri()));
            } else {
                send(sender, PREFIX + "<red>Flare is already running.</red>");
                return 0;
            }
        } catch (final UserReportableException e) {
            send(sender, PREFIX + "<red>Flare failed to start: " + MM.escapeTags(e.getUserError()) + "</red>");
            logger().warn("Error starting Flare", e);
            return 0;
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int stop(final CommandContext<CommandSource> ctx) {
        if (!ProfilingManager.isProfiling()) {
            send(ctx.getSource().sender(), PREFIX + "<red>Flare is not running.</red>");
            return 0;
        }

        ProfilingManager.stop();
        return Command.SINGLE_SUCCESS;
    }

    private static int status(final CommandContext<CommandSource> ctx) {
        final CommandSender sender = ctx.getSource().sender();

        if (!ProfilingManager.isProfiling()) {
            send(sender, PREFIX + "<red>Flare is not running.</red>");
            return 0;
        }

        final long seconds = ProfilingManager.getTimeRan().toSeconds();
        send(sender, PREFIX + MAIN + "Flare has been running for " + seconds + "s:</color> "
            + link(ProfilingManager.getProfilingUri()));

        return Command.SINGLE_SUCCESS;
    }

    private static int usage(final CommandContext<CommandSource> ctx) {
        final String flags = String.join("|", PROFILE_TYPES.stream().map(Map.Entry::getKey).toList());
        send(ctx.getSource().sender(), PREFIX + MAIN + "Usage:</color> " + HEX
            + "/" + COMMAND_NAME + " profiler start [" + flags + "] | stop | status</color>");
        return Command.SINGLE_SUCCESS;
    }

    private static void send(final CommandSender sender, final String message) {
        sender.sendMessage(MM.deserialize(message));
    }

    private static String link(final String uri) {
        return "<click:open_url:'" + uri + "'>" + HEX + uri + "</color></click>";
    }

    public static void broadcastStopped(final String profilingUri) {
        broadcast(PREFIX + MAIN + "Profiling has been stopped.</color> " + link(profilingUri));
        logger().info("Profiling has been stopped: {}", profilingUri);
    }

    public static void broadcastException() {
        broadcast(PREFIX + "<red>Profiling failed, check the console for details.</red>");
    }

    private static void broadcast(final String message) {
        final FlarePlatformFidorial platform = FlarePlatformFidorial.getInstance();
        if (platform == null) {
            return;
        }

        platform.context().server().sendMessage(MM.deserialize(message));
    }

    private static org.slf4j.Logger logger() {
        final FlarePlatformFidorial platform = FlarePlatformFidorial.getInstance();
        if (platform == null) {
            throw new IllegalStateException("Flare plugin is not loaded");
        }
        return platform.context().logger();
    }
}
