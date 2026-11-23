package net.aero.visuals.regulator;

import net.aero.regulator.protocol.RegulatorProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Raw bytes match Bukkit plugin messages; no writeString/writeByteArray prefix is added. */
public record LitePayload(byte[] data) implements CustomPacketPayload {
    public static final Type<LitePayload> ID=new Type<>(Identifier.fromNamespaceAndPath("liteapi","feature-control"));
    public static final StreamCodec<RegistryFriendlyByteBuf,LitePayload> CODEC=new StreamCodec<>() {
        @Override public LitePayload decode(RegistryFriendlyByteBuf buffer){
            int count=buffer.readableBytes();
            if(count>RegulatorProtocol.MAX_BYTES){buffer.skipBytes(count);return new LitePayload(new byte[0]);}
            byte[] bytes=new byte[count];buffer.readBytes(bytes);return new LitePayload(bytes);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer,LitePayload payload){
            if(payload.data.length>RegulatorProtocol.MAX_BYTES)throw new IllegalArgumentException("Regulator payload is too large");
            buffer.writeBytes(payload.data);
        }
    };
    public LitePayload {data=data.clone();}
    @Override public byte[] data(){return data.clone();}
    @Override public Type<? extends CustomPacketPayload> type(){return ID;}
}
