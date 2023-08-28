package xyz.bluspring.idiotproofing;

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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class IdiotProofing implements ModInitializer {
    private static List<Pair<String, Version>> requiredMods = new LinkedList<>();

    /**
     * Runs the mod initializer.
     */
    @Override
    public void onInitialize() {
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            var entryPoints = FabricLoader.getInstance().getEntrypointContainers("client", ClientModInitializer.class);

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
