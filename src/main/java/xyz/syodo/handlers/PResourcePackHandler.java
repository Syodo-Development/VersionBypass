package xyz.syodo.handlers;

import cn.nukkit.Server;
import cn.nukkit.network.connection.BedrockSession;
import cn.nukkit.network.process.SessionState;
import cn.nukkit.network.process.handler.BedrockSessionPacketHandler;
import cn.nukkit.network.protocol.*;
import cn.nukkit.resourcepacks.ResourcePack;
import cn.nukkit.utils.version.Version;
import xyz.syodo.manager.ProtocolManager;
import xyz.syodo.manager.ProtocolPlayer;
import xyz.syodo.packets.PResourcePacksInfoPacket;

import java.util.UUID;

public class PResourcePackHandler extends BedrockSessionPacketHandler {

    public PResourcePackHandler(BedrockSession session, UUID uuid) {
        super(session);
        ProtocolPlayer protocolPlayer = ProtocolManager.get(uuid);
        PResourcePacksInfoPacket infoPacket = new PResourcePacksInfoPacket();
        infoPacket.setPlayer(protocolPlayer);
        infoPacket.resourcePackEntries = session.getServer().getResourcePackManager().getResourceStack();
        infoPacket.mustAccept = session.getServer().getForceResources();
        infoPacket.worldTemplateId = UUID.randomUUID();
        infoPacket.worldTemplateVersion = "";
        session.sendPacket(infoPacket);
    }

    @Override
    public void handle(ResourcePackClientResponsePacket pk) {
        var server = session.getServer();
        switch (pk.responseStatus) {
            case ResourcePackClientResponsePacket.STATUS_REFUSED -> {
                this.session.close("disconnectionScreen.noReason");
            }
            case ResourcePackClientResponsePacket.STATUS_SEND_PACKS -> {
                for (ResourcePackClientResponsePacket.Entry entry : pk.packEntries) {
                    ResourcePack resourcePack = server.getResourcePackManager().getPackById(entry.uuid);
                    if (resourcePack == null) {
                        this.session.close("disconnectionScreen.resourcePack");
                        return;
                    }

                    ResourcePackDataInfoPacket dataInfoPacket = new ResourcePackDataInfoPacket();
                    dataInfoPacket.packId = resourcePack.getPackId();
                    dataInfoPacket.setPackVersion(new Version(resourcePack.getPackVersion()));
                    dataInfoPacket.maxChunkSize = server.getResourcePackManager().getMaxChunkSize();
                    dataInfoPacket.chunkCount = (int) Math.ceil(resourcePack.getPackSize() / (double) dataInfoPacket.maxChunkSize);
                    dataInfoPacket.compressedPackSize = resourcePack.getPackSize();
                    dataInfoPacket.sha256 = resourcePack.getSha256();
                    session.sendPacket(dataInfoPacket);
                }
            }
            case ResourcePackClientResponsePacket.STATUS_HAVE_ALL_PACKS -> {
                ResourcePackStackPacket stackPacket = new ResourcePackStackPacket();
                stackPacket.mustAccept = server.getForceResources() && !server.getForceResourcesAllowOwnPacks();
                stackPacket.resourcePackStack = server.getResourcePackManager().getResourceStack();
                stackPacket.experiments.add(
                        new ResourcePackStackPacket.ExperimentData("data_driven_items", true)
                );
                stackPacket.experiments.add(
                        new ResourcePackStackPacket.ExperimentData("data_driven_biomes", true)
                );
                stackPacket.experiments.add(
                        new ResourcePackStackPacket.ExperimentData("upcoming_creator_features", true)
                );
                stackPacket.experiments.add(
                        new ResourcePackStackPacket.ExperimentData("gametest", true)
                );
                stackPacket.experiments.add(
                        new ResourcePackStackPacket.ExperimentData("experimental_molang_features", true)
                );
                stackPacket.experiments.add(
                        new ResourcePackStackPacket.ExperimentData("cameras", true)
                );
                session.sendPacket(stackPacket);
            }
            case ResourcePackClientResponsePacket.STATUS_COMPLETED -> {
                this.session.getMachine().fire(SessionState.PRE_SPAWN);
            }
        }
    }

    @Override
    public void handle(ResourcePackChunkRequestPacket pk) {
        // TODO: Pack version check
        var mgr = session.getServer().getResourcePackManager();
        ResourcePack resourcePack = mgr.getPackById(pk.getPackId());
        if (resourcePack == null) {
            this.session.close("disconnectionScreen.resourcePack");
            return;
        }
        int maxChunkSize = mgr.getMaxChunkSize();
        ResourcePackChunkDataPacket dataPacket = new ResourcePackChunkDataPacket();
        dataPacket.setPackId(resourcePack.getPackId());
        dataPacket.setPackVersion(new Version(resourcePack.getPackVersion()));
        dataPacket.chunkIndex = pk.chunkIndex;
        dataPacket.data = resourcePack.getPackChunk(maxChunkSize * pk.chunkIndex, maxChunkSize);
        dataPacket.progress = maxChunkSize * (long) pk.chunkIndex;
        session.sendPacket(dataPacket);
    }
}
