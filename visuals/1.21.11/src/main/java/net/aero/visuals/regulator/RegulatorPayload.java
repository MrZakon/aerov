package net.aero.visuals.regulator;

import net.aero.regulator.protocol.RegulatorProtocol;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Raw bytes match Bukkit plugin messages; no writeString/writeByteArray prefix is added. */
public record RegulatorPayload(byte[] data) implements CustomPayload {
    public static final Id<RegulatorPayload> ID=new Id<>(Identifier.of("aerovisuals","regulator"));
    public static final PacketCodec<RegistryByteBuf,RegulatorPayload> CODEC=new PacketCodec<>() {
        @Override public RegulatorPayload decode(RegistryByteBuf buffer){
            int count=buffer.readableBytes();
            if(count>RegulatorProtocol.MAX_BYTES){buffer.skipBytes(count);return new RegulatorPayload(new byte[0]);}
            byte[] bytes=new byte[count];buffer.readBytes(bytes);return new RegulatorPayload(bytes);
        }
        @Override public void encode(RegistryByteBuf buffer,RegulatorPayload payload){
            if(payload.data.length>RegulatorProtocol.MAX_BYTES)throw new IllegalArgumentException("Regulator payload is too large");
            buffer.writeBytes(payload.data);
        }
    };
    public RegulatorPayload {data=data.clone();}
    @Override public byte[] data(){return data.clone();}
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
