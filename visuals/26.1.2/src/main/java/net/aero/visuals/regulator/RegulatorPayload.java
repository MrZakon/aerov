package net.aero.visuals.regulator;

import net.aero.regulator.protocol.RegulatorProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Raw bytes match Bukkit plugin messages; no writeString/writeByteArray prefix is added. */
public record RegulatorPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<RegulatorPayload> ID=new Type<>(Identifier.fromNamespaceAndPath("aerovisuals","regulator"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RegulatorPayload> CODEC=new StreamCodec<>() {
        @Override public RegulatorPayload decode(RegistryFriendlyByteBuf buffer){
            int count=buffer.readableBytes();
            if(count>RegulatorProtocol.MAX_BYTES){buffer.skipBytes(count);return new RegulatorPayload(new byte[0]);}
            byte[] bytes=new byte[count];buffer.readBytes(bytes);return new RegulatorPayload(bytes);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer,RegulatorPayload payload){
            if(payload.data.length>RegulatorProtocol.MAX_BYTES)throw new IllegalArgumentException("Regulator payload is too large");
            buffer.writeBytes(payload.data);
        }
    };
    public RegulatorPayload {data=data.clone();}
    @Override public byte[] data(){return data.clone();}
    @Override public Type<? extends CustomPacketPayload> type(){return ID;}
}
