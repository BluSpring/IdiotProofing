package xyz.bluspring.idiotproofing;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedList;
import java.util.List;

public class IdiotProofing implements ModInitializer {
    private static List<String> requiredMods = new LinkedList<>();

    /**
     * Runs the mod initializer.
     */
    @Override
    public void onInitialize() {
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            if (mod.getMetadata().getEnvironment() == ModEnvironment.UNIVERSAL)
                requiredMods.add(mod.getMetadata().getId());
        }

        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            sender.sendPacket(new ResourceLocation("idiotproofing", "mod_check"), PacketByteBufs.empty());
        });

        ServerLoginNetworking.registerGlobalReceiver(new ResourceLocation("idiotproofing", "mod_check"), (server, handler, understood, buf, sync, sender) -> {
            if (!understood) {
                handler.disconnect(Component.literal("you did not install the mods correctly."));
                return;
            }

            var totalMods = buf.readInt();
            var totalFound = 0;

            for (int i = 0; i < totalMods; i++) {
                var modId = buf.readUtf();

                if (requiredMods.contains(modId))
                    totalFound++;
            }

            if (totalFound < requiredMods.size()) {
                handler.disconnect(Component.literal("you did not install the mods correctly."));
            }
        });
    }
}
