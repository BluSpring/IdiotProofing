package xyz.bluspring.idiotproofing.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.CompletableFuture;

public class IdiotProofingClient implements ClientModInitializer {
    /**
     * Runs the mod initializer on the client environment.
     */
    @Override
    public void onInitializeClient() {
        ClientLoginNetworking.registerGlobalReceiver(new ResourceLocation("idiotproofing", "mod_check"), (client, handler, buf, listener) -> {
            var byteBuf = PacketByteBufs.create();

            byteBuf.writeInt(FabricLoader.getInstance().getAllMods().size());
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                byteBuf.writeUtf(mod.getMetadata().getId());
            }

            return CompletableFuture.completedFuture(byteBuf);
        });
    }
}
