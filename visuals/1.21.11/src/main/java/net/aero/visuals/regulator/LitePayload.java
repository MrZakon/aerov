package net.aero.visuals.regulator;

import net.aero.regulator.protocol.RegulatorProtocol;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Raw bytes match Bukkit plugin messages; no writeString/writeByteArray prefix is added. */
public record LitePayload(byte[] data) implements CustomPayload {
    public static final Id<LitePayload> ID=new Id<>(Identifier.of("liteapi","feature-control"));
    public static final PacketCodec<RegistryByteBuf,LitePayload> CODEC=new PacketCodec<>() {
        @Override public LitePayload decode(RegistryByteBuf buffer){
            int count=buffer.readableBytes();
            if(count>RegulatorProtocol.MAX_BYTES){buffer.skipBytes(count);return new LitePayload(new byte[0]);}
            byte[] bytes=new byte[count];buffer.readBytes(bytes);return new LitePayload(bytes);
        }
        @Override public void encode(RegistryByteBuf buffer,LitePayload payload){
            if(payload.data.length>RegulatorProtocol.MAX_BYTES)throw new IllegalArgumentException("Regulator payload is too large");
            buffer.writeBytes(payload.data);
        }
    };
    public LitePayload {data=data.clone();}
    @Override public byte[] data(){return data.clone();}
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
