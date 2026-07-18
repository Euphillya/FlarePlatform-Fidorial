package co.technove.flareplatform.fidorial.manager;

import co.technove.flare.Flare;
import co.technove.flare.FlareAuth;
import co.technove.flare.FlareBuilder;
import co.technove.flare.exceptions.UserReportableException;
import co.technove.flare.internal.profiling.ProfileType;
import co.technove.flareplatform.common.CustomCategories;
import co.technove.flareplatform.common.collectors.GCEventCollector;
import co.technove.flareplatform.common.collectors.StatCollector;
import co.technove.flareplatform.fidorial.FlarePlatformFidorial;
import co.technove.flareplatform.fidorial.collectors.FidorialCountCollector;
import co.technove.flareplatform.fidorial.collectors.FidorialThreadCollector;
import co.technove.flareplatform.fidorial.collectors.RegionTpsCollector;
import co.technove.flareplatform.fidorial.command.FlareCommand;
import co.technove.flareplatform.fidorial.config.FlareFidorialConfig;
import co.technove.flareplatform.fidorial.utils.PluginLookup;
import co.technove.flareplatform.fidorial.utils.ServerConfigurations;
import fr.fidorial.Server;
import org.jspecify.annotations.Nullable;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.VirtualMemory;
import oshi.software.os.OperatingSystem;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ProfilingManager {

    private static final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1, r -> {
        Thread t = new Thread(r);
        t.setName("Flare Profiling Manager Thread");
        t.setDaemon(true);
        return t;
    });

    public static @Nullable ScheduledFuture<?> currentTask;
    private static @Nullable Flare currentFlare;

    public static synchronized boolean isProfiling() {
        return currentFlare != null && currentFlare.isRunning();
    }

    public static synchronized String getProfilingUri() {
        if (currentFlare == null) {
            return "Flare is not running";
        }
        FlareFidorialConfig config = platform().config();
        return currentFlare.getURI()
            .map(URI::toString)
            .map(s -> {
                if (!config.viewerUrl().isBlank()) {
                    return s.replace(config.backendUrl().toString(), config.viewerUrl());
                }
                return s;
            })
            .orElse("Flare is not running");
    }

    public static Duration getTimeRan() {
        Flare flare = currentFlare;
        if (flare == null) {
            return Duration.ofMillis(0);
        }
        return flare.getCurrentDuration();
    }

    public static synchronized boolean start(ProfileType profileType) throws UserReportableException {
        if (currentFlare != null && !currentFlare.isRunning()) {
            currentFlare = null;
        }
        if (ProfilingManager.isProfiling()) {
            return false;
        }

        FlarePlatformFidorial platform = platform();
        Server server = platform.context().server();
        FlareFidorialConfig config = platform.config();

        try {
            SystemInfo systemInfo = new SystemInfo();
            OperatingSystem os = systemInfo.getOperatingSystem();
            HardwareAbstractionLayer hardware = systemInfo.getHardware();

            CentralProcessor processor = hardware.getProcessor();
            CentralProcessor.ProcessorIdentifier processorIdentifier = processor.getProcessorIdentifier();

            GlobalMemory memory = hardware.getMemory();
            VirtualMemory virtualMemory = memory.getVirtualMemory();

            FlareBuilder builder = new FlareBuilder()
                .withProfileType(profileType)
                .withMemoryProfiling(true)
                .withAuth(FlareAuth.fromTokenAndUrl(config.token(), config.backendUrl()))

                .withFiles(ServerConfigurations.getCleanCopies())
                .withVersion("Primary Version",
                    "Fidorial | Minecraft " + server.minecraftVersion()
                        + " (protocol " + server.protocolVersion() + ")")
                .withVersion("Fidorial Version", server.minecraftVersion())

                .withGraphCategories(CustomCategories.PERF)
                .withCollectors(new GCEventCollector(), new StatCollector(),
                    new FidorialThreadCollector(),
                    new FidorialCountCollector(server),
                    new RegionTpsCollector(server))
                .withClassIdentifier(ProfilingManager::pluginForClass)

                .withHardware(new FlareBuilder.HardwareBuilder()
                    .setCoreCount(processor.getPhysicalProcessorCount())
                    .setThreadCount(processor.getLogicalProcessorCount())
                    .setCpuModel(processorIdentifier.getName())
                    .setCpuFrequency(processor.getMaxFreq())

                    .setTotalMemory(memory.getTotal())
                    .setTotalSwap(virtualMemory.getSwapTotal())
                    .setTotalVirtual(virtualMemory.getVirtualMax())
                )

                .withOperatingSystem(new FlareBuilder.OperatingSystemBuilder()
                    .setManufacturer(os.getManufacturer())
                    .setFamily(os.getFamily())
                    .setVersion(os.getVersionInfo().toString())
                    .setBitness(os.getBitness())
                )

                .withExceptionRunnable(() -> {
                    try {
                        if (currentTask != null) {
                            currentTask.cancel(true);
                        }
                    } catch (Throwable t) {
                        platform().context().logger().warn("Error occurred stopping Flare", t);
                    } finally {
                        currentTask = null;
                    }
                    FlareCommand.broadcastException();
                });

            currentFlare = builder.build();
        } catch (IOException e) {
            platform.context().logger().warn("Failed to read configuration files:", e);
            throw new UserReportableException("Failed to load configuration files, check logs for further details.");
        }
        try {
            currentFlare.start();
        } catch (IllegalStateException e) {
            platform.context().logger().warn("Error starting Flare:", e);
            throw new UserReportableException("Failed to start Flare, check logs for further details.");
        }

        currentTask = scheduler.schedule(ProfilingManager::stop, 15, TimeUnit.MINUTES);
        return true;
    }

    public static synchronized boolean stop() {
        if (!isProfiling()) {
            return false;
        }
        if (currentFlare != null && !currentFlare.isRunning()) {
            currentFlare = null;
            return true;
        }
        String profilingUri = ProfilingManager.getProfilingUri();
        FlareCommand.broadcastStopped(profilingUri);
        try {
            currentFlare.stop();
        } catch (IllegalStateException e) {
            platform().context().logger().warn("Error occurred stopping Flare", e);
        }
        currentFlare = null;

        try {
            if (currentTask != null) {
                currentTask.cancel(true);
            }
        } catch (Throwable t) {
            platform().context().logger().warn("Error occurred stopping Flare", t);
        }
        currentTask = null;

        return true;
    }

    public static void shutdown() {
        scheduler.shutdownNow();
    }

    private static Optional<String> pluginForClass(String name) {
        PluginLookup lookup = platform().getPluginLookup();
        return lookup == null ? Optional.empty() : lookup.getPluginForClass(name);
    }

    private static FlarePlatformFidorial platform() {
        FlarePlatformFidorial instance = FlarePlatformFidorial.getInstance();
        if (instance == null) {
            throw new IllegalStateException("Flare plugin is not loaded");
        }
        return instance;
    }
}
