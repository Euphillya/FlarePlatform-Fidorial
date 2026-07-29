package co.technove.flareplatform.fidorial;

import co.technove.flare.FlareInitializer;
import co.technove.flare.internal.profiling.InitializationException;
import co.technove.flareplatform.fidorial.command.FlareCommand;
import co.technove.flareplatform.fidorial.config.FlareFidorialConfig;
import co.technove.flareplatform.fidorial.manager.ProfilingManager;
import co.technove.flareplatform.fidorial.utils.PluginLookup;
import fr.fidorial.entity.Player;
import fr.fidorial.plugin.Plugin;
import fr.fidorial.plugin.PluginContext;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public class FlarePlatformFidorial implements Plugin {

    private static @Nullable FlarePlatformFidorial instance;

    private @Nullable PluginContext context;
    private @Nullable FlareFidorialConfig config;
    private @Nullable PluginLookup lookup;

    private boolean registered;

    public static @Nullable FlarePlatformFidorial getInstance() {
        return instance;
    }

    @Override
    public void onLoad(final PluginContext context) {
        this.context = context;
        instance = this;
    }

    @Override
    public void onEnable() {
        final PluginContext ctx = context();

        this.config = new FlareFidorialConfig(ctx.dataFolder(), ctx.logger());
        this.config.load();

        // detect unsupported platforms
        final String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (!osName.contains("linux") && !osName.contains("mac")) {
            ctx.logger().warn("Flare does not support running on {}, will not enable!", osName);
            return;
        }

        try {
            final List<String> warnings = FlareInitializer.initialize();
            if (!warnings.isEmpty()) {
                ctx.logger().warn("Warnings while initializing Flare: {}", String.join(", ", warnings));
            }

            ctx.server().commands().register(FlareCommand.create());
            this.registered = true;
            refreshCommands();

            this.lookup = new PluginLookup(ctx.server());
        } catch (final InitializationException e) {
            ctx.logger().error("Failed to initialize Flare", e);
        }
    }

    @Override
    public void onDisable() {
        if (ProfilingManager.isProfiling()) {
            ProfilingManager.stop();
        }

        if (this.registered && this.context != null) {
            this.context.server().commands().unregister(FlareCommand.COMMAND_NAME);
            refreshCommands();
            this.registered = false;
        }

        ProfilingManager.shutdown();

        if (instance == this) {
            instance = null;
        }
    }

    private void refreshCommands() {
        for (final Player player : context().server().onlinePlayers()) {
            player.refreshCommands();
        }
    }

    public PluginContext context() {
        if (this.context == null) {
            throw new IllegalStateException("Plugin not loaded yet");
        }
        return this.context;
    }

    public FlareFidorialConfig config() {
        if (this.config == null) {
            throw new IllegalStateException("Config not loaded yet");
        }
        return this.config;
    }

    public @Nullable PluginLookup getPluginLookup() {
        return this.lookup;
    }
}
