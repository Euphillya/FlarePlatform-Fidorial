package co.technove.flareplatform.fidorial;

import co.technove.flare.FlareInitializer;
import co.technove.flare.internal.profiling.InitializationException;
import co.technove.flareplatform.fidorial.command.FlareCommand;
import co.technove.flareplatform.fidorial.config.FlareFidorialConfig;
import co.technove.flareplatform.fidorial.manager.ProfilingManager;
import co.technove.flareplatform.fidorial.utils.PluginLookup;
import fr.euphyllia.fidorial.api.plugin.Plugin;
import fr.euphyllia.fidorial.api.plugin.PluginContext;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public class FlarePlatformFidorial implements Plugin {

    private static @Nullable FlarePlatformFidorial instance;
    private static boolean shouldRegister = true;

    private @Nullable PluginContext context;
    private @Nullable FlareFidorialConfig config;
    private @Nullable PluginLookup lookup;

    public static @Nullable FlarePlatformFidorial getInstance() {
        return instance;
    }

    @Override
    public void onLoad(PluginContext context) {
        this.context = context;
        instance = this;
    }

    @Override
    public void onEnable() {
        PluginContext ctx = context();
        this.config = new FlareFidorialConfig(ctx.dataFolder(), ctx.logger());
        this.config.load();

        // detect unsupported platforms
        final String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (!osName.contains("linux") && !osName.contains("mac")) {
            ctx.logger().warn("Flare does not support running on {}, will not enable!", osName);
            shouldRegister = false;
        }

        if (!shouldRegister) {
            return;
        }

        try {
            final List<String> warnings = FlareInitializer.initialize();
            if (!warnings.isEmpty()) {
                ctx.logger().warn("Warnings while initializing Flare: {}", String.join(", ", warnings));
            }
            ctx.server().commands().register("flare", new FlareCommand());
            this.lookup = new PluginLookup(ctx.server());
        } catch (InitializationException e) {
            ctx.logger().error("Failed to initialize Flare", e);
        }
    }

    @Override
    public void onDisable() {
        if (ProfilingManager.isProfiling()) {
            ProfilingManager.stop();
        }
        if (context != null) {
            context.server().commands().unregister("flare");
        }
        ProfilingManager.shutdown();
    }

    public PluginContext context() {
        if (context == null) {
            throw new IllegalStateException("Plugin not loaded yet");
        }
        return context;
    }

    public FlareFidorialConfig config() {
        if (config == null) {
            throw new IllegalStateException("Config not loaded yet");
        }
        return config;
    }

    public @Nullable PluginLookup getPluginLookup() {
        return lookup;
    }
}
