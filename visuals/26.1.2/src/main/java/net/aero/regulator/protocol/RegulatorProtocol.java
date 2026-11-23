package net.aero.regulator.protocol;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.Pattern;

/** AeroProject protocol 1. Raw UTF-8 JSON; intentionally no Minecraft string/array length prefix. */
public final class RegulatorProtocol {
    // Even 128 maximum-length ids/reasons with every ASCII quote escaped fit under MAX_BYTES.
    public static final int VERSION=1, MAX_BYTES=30_000, MAX_FEATURES=128, MAX_REASON_BYTES=80;
    public static final String CHANNEL="aerovisuals:regulator";
    private static final Pattern ID=Pattern.compile("[a-z0-9_]{1,64}");
    private RegulatorProtocol(){}

    public sealed interface Packet permits Hello, Request, Policy, Ack, Failure {}
    public record Hello(String nonce,String clientVersion,String minecraftVersion,List<String> features) implements Packet {
        public Hello {uuid(nonce);text(clientVersion,64);text(minecraftVersion,64);features=List.copyOf(features);ids(features);}
    }
    public record Request(String session) implements Packet {public Request {uuid(session);}}
    public record Policy(String session,String nonce,long revision,Map<String,String> blocked) implements Packet {
        public Policy {
            uuid(session);uuid(nonce);RegulatorProtocol.revision(revision);
            if(blocked.size()>MAX_FEATURES)throw bad("Too many restrictions");
            var copy=new LinkedHashMap<String,String>();
            blocked.forEach((id,reason)->{featureId(id);reason(reason);copy.put(id,reason);});
            blocked=Collections.unmodifiableMap(copy);
        }
    }
    public record Ack(String session,String nonce,long revision) implements Packet {public Ack {uuid(session);uuid(nonce);RegulatorProtocol.revision(revision);}}
    public record Failure(String code) implements Packet {
        public Failure {if(!Set.of("BAD_REQUEST","RATE_LIMITED","UNSUPPORTED_VERSION","INTERNAL_ERROR").contains(code))throw bad("Unknown error code");}
    }

    public static boolean validId(String id){return id!=null&&ID.matcher(id).matches();}
    public static String featureId(String id){if(!validId(id))throw bad("Invalid feature identifier");return id;}
    public static String reason(String value){text(value,MAX_REASON_BYTES);return value;}
    private static void text(String value,int bytes){
        if(value==null||value.isBlank()||value.getBytes(StandardCharsets.UTF_8).length>bytes)throw bad("Text must be non-blank and at most "+bytes+" UTF-8 bytes");
        if(value.codePoints().anyMatch(c->Character.isISOControl(c)||c==0x2028||c==0x2029||c>=0xD800&&c<=0xDFFF))throw bad("Control characters or unpaired surrogates in text");
    }
    private static void uuid(String value){
        if(value==null||value.length()!=36)throw bad("Invalid session identifier");
        try{if(!UUID.fromString(value).toString().equals(value))throw bad("Invalid session identifier");}catch(IllegalArgumentException e){throw bad("Invalid session identifier");}
    }
    private static void revision(long value){if(value<1)throw bad("Invalid policy revision");}
    private static void ids(Collection<String> values){
        if(values.isEmpty()||values.size()>MAX_FEATURES)throw bad("Invalid feature count");
        var unique=new HashSet<String>();for(String id:values){featureId(id);if(!unique.add(id))throw bad("Duplicate feature identifier");}
    }
    private static IllegalArgumentException bad(String message){return new IllegalArgumentException(message);}

    public static byte[] encode(Packet packet){
        try {
            StringWriter output=new StringWriter();JsonWriter w=new JsonWriter(output);w.setLenient(false);
            w.beginObject().name("v").value(VERSION);
            if(packet instanceof Hello h){
                w.name("type").value("hello").name("client").value("aerovisuals").name("nonce").value(h.nonce()).name("clientVersion").value(h.clientVersion()).name("minecraftVersion").value(h.minecraftVersion()).name("features").beginArray();
                for(String feature:h.features())w.value(feature);w.endArray();
            } else if(packet instanceof Request r){w.name("type").value("request").name("session").value(r.session());
            } else if(packet instanceof Policy p){
                w.name("type").value("policy").name("session").value(p.session()).name("nonce").value(p.nonce()).name("revision").value(p.revision()).name("blocked").beginObject();
                for(var entry:p.blocked().entrySet())w.name(entry.getKey()).value(entry.getValue());w.endObject();
            } else if(packet instanceof Ack a){w.name("type").value("ack").name("session").value(a.session()).name("nonce").value(a.nonce()).name("revision").value(a.revision());
            } else if(packet instanceof Failure e){w.name("type").value("error").name("code").value(e.code());
            } else throw bad("Unsupported packet");
            w.endObject();w.close();byte[] bytes=output.toString().getBytes(StandardCharsets.UTF_8);
            if(bytes.length>MAX_BYTES)throw bad("Packet is too large");return bytes;
        }catch(IOException impossible){throw new IllegalStateException("Cannot encode regulator packet",impossible);}
    }

    public static Packet decode(byte[] bytes){
        if(bytes==null||bytes.length==0||bytes.length>MAX_BYTES)throw bad("Invalid packet size");
        try {
            String json=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonReader r=new JsonReader(new StringReader(json));r.setLenient(false);
            if(r.peek()!=JsonToken.BEGIN_OBJECT)throw bad("Expected envelope object");r.beginObject();
            var seen=new HashSet<String>();String type=null,nonce=null,session=null,client=null,clientVersion=null,minecraft=null,code=null;Long revision=null;Integer version=null;
            List<String> features=null;Map<String,String> blocked=null;
            while(r.hasNext()){
                String key=r.nextName();if(key.length()>24||!seen.add(key))throw bad("Unknown or duplicate envelope property");
                switch(key){
                    case "v" -> {long n=integer(r);if(n!=VERSION)throw bad("Unsupported protocol version");version=(int)n;}
                    case "type" -> type=string(r,16);
                    case "nonce" -> nonce=string(r,36);
                    case "session" -> session=string(r,36);
                    case "client" -> client=string(r,16);
                    case "clientVersion" -> clientVersion=string(r,64);
                    case "minecraftVersion" -> minecraft=string(r,64);
                    case "code" -> code=string(r,32);
                    case "revision" -> revision=integer(r);
                    case "features" -> {
                        if(r.peek()!=JsonToken.BEGIN_ARRAY)throw bad("Expected feature array");r.beginArray();features=new ArrayList<>();
                        while(r.hasNext()){if(features.size()>=MAX_FEATURES)throw bad("Too many features");features.add(string(r,64));}r.endArray();
                    }
                    case "blocked" -> {
                        if(r.peek()!=JsonToken.BEGIN_OBJECT)throw bad("Expected restriction object");r.beginObject();blocked=new LinkedHashMap<>();
                        while(r.hasNext()){
                            if(blocked.size()>=MAX_FEATURES)throw bad("Too many restrictions");String id=r.nextName();featureId(id);String value=string(r,MAX_REASON_BYTES);reason(value);
                            if(blocked.putIfAbsent(id,value)!=null)throw bad("Duplicate restriction");
                        }r.endObject();
                    }
                    default -> throw bad("Unknown envelope property");
                }
            }
            r.endObject();if(r.peek()!=JsonToken.END_DOCUMENT||version==null||type==null)throw bad("Incomplete envelope or trailing data");r.close();
            return switch(type){
                case "hello" -> {fields(seen,"v","type","client","nonce","clientVersion","minecraftVersion","features");if(!"aerovisuals".equals(client))throw bad("Unknown client");yield new Hello(nonce,clientVersion,minecraft,features);}
                case "request" -> {fields(seen,"v","type","session");yield new Request(session);}
                case "policy" -> {fields(seen,"v","type","session","nonce","revision","blocked");yield new Policy(session,nonce,revision,blocked);}
                case "ack" -> {fields(seen,"v","type","session","nonce","revision");yield new Ack(session,nonce,revision);}
                case "error" -> {fields(seen,"v","type","code");yield new Failure(code);}
                default -> throw bad("Unknown packet type");
            };
        }catch(IOException|IllegalStateException|NullPointerException e){throw bad("Malformed regulator packet");}
    }
    private static String string(JsonReader r,int maxBytes)throws IOException{if(r.peek()!=JsonToken.STRING)throw bad("Expected JSON string");String value=r.nextString();text(value,maxBytes);return value;}
    private static long integer(JsonReader r)throws IOException{if(r.peek()!=JsonToken.NUMBER)throw bad("Expected JSON integer");try{return Long.parseLong(r.nextString());}catch(NumberFormatException invalid){throw bad("Invalid JSON integer");}}
    private static void fields(Set<String> actual,String... expected){if(!actual.equals(Set.of(expected)))throw bad("Incorrect packet properties");}
}
