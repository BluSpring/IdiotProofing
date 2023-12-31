package xyz.bluspring.idiotproofing;

import com.google.common.io.Files;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

public class IdiotProofing implements ModInitializer {
    private static final List<Pair<String, Version>> requiredMods = new LinkedList<>();
    private static final List<String> included = new LinkedList<>();
    private static final List<String> excluded = new LinkedList<>();
    private static final List<String> unsupported = new LinkedList<>();
    private static final Properties properties = new Properties();

    /**
     * Runs the mod initializer.
     */
    @Override
    public void onInitialize() {
        var configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "idiotproofing.properties");

        properties.setProperty("includes", "");
        properties.setProperty("excludes", "");
        properties.setProperty("unsupported", "");

        if (configFile.exists()) {
            try {
                properties.load(Files.newReader(configFile, Charset.defaultCharset()));

                included.addAll(Arrays.stream(properties.getProperty("includes", "").split(",")).toList());
                excluded.addAll(Arrays.stream(properties.getProperty("excludes", "").split(",")).toList());
                unsupported.addAll(Arrays.stream(properties.getProperty("unsupported", "").split(",")).toList());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                configFile.createNewFile();

                properties.store(Files.newWriter(configFile, Charset.defaultCharset()), "");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        var file = new File(FabricLoader.getInstance().getConfigDir().toFile(), "idiotproofing_excludes.txt");

        if (file.exists()) {
            try {
                var lines = Files.readLines(file, Charset.defaultCharset());
                for (String line : lines) {
                    excluded.add(line.trim());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        var entryPoints = FabricLoader.getInstance().getEntrypointContainers("client", ClientModInitializer.class);
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            if (excluded.contains(mod.getMetadata().getId()))
                continue;

            if (!included.isEmpty() && !included.contains(mod.getMetadata().getId()))
                continue;

            if (mod.getMetadata().getEnvironment() == ModEnvironment.UNIVERSAL) {
                // Double check to make sure the mod is supported on the client-side too
                if (entryPoints.stream().anyMatch(ep -> ep.getProvider().getMetadata().getId().equals(mod.getMetadata().getId())))
                    requiredMods.add(Pair.of(mod.getMetadata().getId(), mod.getMetadata().getVersion()));
            }
        }

        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            sender.sendPacket(new ResourceLocation("idiotproofing", "mod_check"), PacketByteBufs.empty());
        });

        ServerLoginNetworking.registerGlobalReceiver(new ResourceLocation("idiotproofing", "mod_check"), (server, handler, understood, buf, sync, sender) -> {
            if (!understood) {
                handler.disconnect(Component.literal("you did not install the mods correctly. you are either missing everything, or forgot to install the IdiotProofing mod."));
                return;
            }

            var totalMods = buf.readInt();
            var totalFound = 0;
            var foundMods = new ArrayList<String>();
            var mismatches = new ArrayList<Pair<String, Version>>();
            var unsupportedMods = new ArrayList<String>();

            for (int i = 0; i < totalMods; i++) {
                var modId = buf.readUtf();
                Version version;
                try {
                    version = Version.parse(buf.readUtf());
                } catch (VersionParsingException e) {
                    handler.disconnect(Component.literal(e.fillInStackTrace().getLocalizedMessage()));
                    return;
                }

                if (requiredMods.stream().anyMatch(a -> a.getFirst().equals(modId))) {
                    var match = requiredMods.stream().filter(a -> a.getFirst().equals(modId)).findFirst().get();

                    if (match.getSecond().compareTo(version) > 0) {
                        mismatches.add(Pair.of(modId, version));
                        continue;
                    }

                    totalFound++;
                    foundMods.add(modId);
                }

                if (unsupported.stream().anyMatch(a -> a.equals(modId))) {
                    unsupportedMods.add(modId);
                }
            }

            if (!unsupportedMods.isEmpty()) {
                StringBuilder message = new StringBuilder();

                for (String mod : unsupportedMods) {
                    message.append(mod + "\n");
                }

                handler.disconnect(Component.literal("you have unsupported mods: \n" + message));
                return;
            }

            if (totalFound < requiredMods.size()) {
                var missingMods = requiredMods.stream().filter(a -> !foundMods.contains(a.getFirst())).toList();
                StringBuilder message = new StringBuilder();

                for (Pair<String, Version> missingMod : missingMods) {
                    message.append(missingMod.getFirst() + "(" + missingMod.getSecond().getFriendlyString() + ")" + "\n");
                }

                for (Pair<String, Version> mismatch : mismatches) {
                    var mod = requiredMods.stream().filter(a -> a.getFirst().equals(mismatch.getFirst())).findFirst().get();
                    message.append(mismatch.getFirst() + "(requires " + mod.getSecond().getFriendlyString() + ", found " + mismatch.getSecond().getFriendlyString() + ")" + "\n");
                }

                handler.disconnect(Component.literal("you are missing: (" + totalFound + "/" + requiredMods.size() + ")\n" + message));
            }
        });
    }
}
