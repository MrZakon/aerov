package net.aero.visuals.guard;

import com.sun.jna.*;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.COM.WbemcliUtil;
import com.sun.jna.win32.W32APIOptions;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.*;

/** Launcher-compatible identifiers; never substitutes a shared or generic ID. */
public final class HardwareId {
    private static volatile String cached;
    private static CompletableFuture<String> pending;
    private static final ExecutorService detector=Executors.newSingleThreadExecutor(task->{var t=new Thread(task,"Aero-Hardware-Identifier");t.setDaemon(true);return t;});
    private enum ProductProperty { UUID }
    public interface Firmware extends com.sun.jna.win32.StdCallLibrary {
        int GetSystemFirmwareTable(int provider,int table,Pointer data,int size);
    }
    private HardwareId(){}
    public static String cached(){return cached==null?"":cached;}
    public static String get()throws Exception{
        String known=cached;if(known!=null)return known;
        CompletableFuture<String> task;
        synchronized(HardwareId.class){if(pending==null)pending=CompletableFuture.supplyAsync(()->{try{return detect();}catch(Exception e){throw new CompletionException(e);}},detector);task=pending;}
        try{known=task.get(8,TimeUnit.SECONDS);}
        catch(TimeoutException timeout){if(!Platform.isWindows())throw new IOException("Hardware identifier timed out");known=windowsFallback();}
        catch(ExecutionException failure){synchronized(HardwareId.class){if(pending==task)pending=null;}throw new IOException("Hardware identifier unavailable",failure.getCause());}
        if(known==null||known.isBlank())throw new IOException("Hardware identifier unavailable");
        cached=known;return known;
    }
    private static String detect()throws Exception{
        if(Platform.isWindows()){String uuid=wmiUuid();return uuid!=null?uuid:windowsFallback();}
        if(Platform.isMac())return macUuid();
        if(Platform.isLinux()){
            String uuid=read("/sys/class/dmi/id/product_uuid");if(validUuid(uuid)&&!uuid.contains("00000000"))return uuid;
            for(String file:new String[]{"/etc/machine-id","/var/lib/dbus/machine-id"}){String id=read(file);if(id.matches("[A-F0-9]{32}")&&!id.matches("0+"))return id;}
        }
        throw new IOException("No supported stable hardware identifier");
    }
    private static String wmiUuid(){
        boolean initialized=false;
        try{
            int result=Ole32.INSTANCE.CoInitializeEx(Pointer.NULL,Ole32.COINIT_MULTITHREADED).intValue();
            initialized=result>=0;if(!initialized)return null;
            var query=new WbemcliUtil.WmiQuery<ProductProperty>("Win32_ComputerSystemProduct",ProductProperty.class);
            var rows=query.execute(2500);if(rows.getResultCount()==0)return null;
            String uuid=String.valueOf(rows.getValue(ProductProperty.UUID,0)).strip().toUpperCase(Locale.ROOT);
            return validUuid(uuid)?uuid:null;
        }catch(Exception|LinkageError ignored){return null;}
        finally{if(initialized)Ole32.INSTANCE.CoUninitialize();}
    }
    private static String windowsFallback()throws IOException{
        String uuid=firmwareUuid();if(uuid!=null)return uuid;
        try{
            String guid=Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE,"SOFTWARE\\Microsoft\\Cryptography","MachineGuid",WinNT.KEY_WOW64_64KEY).strip().toUpperCase(Locale.ROOT);
            if(validUuid(guid))return guid;
        }catch(Exception|LinkageError ignored){}
        throw new IOException("Windows hardware identifier unavailable");
    }
    private static String firmwareUuid(){
        try{
            var kernel=Native.load("kernel32",Firmware.class,W32APIOptions.DEFAULT_OPTIONS);
            int size=kernel.GetSystemFirmwareTable(0x52534D42,0,null,0);if(size<12||size>1_048_576)return null;
            try(Memory memory=new Memory(size)){
                if(kernel.GetSystemFirmwareTable(0x52534D42,0,memory,size)!=size)return null;
                int major=Byte.toUnsignedInt(memory.getByte(1)),minor=Byte.toUnsignedInt(memory.getByte(2));
                int end=(int)Math.min(size,8L+Integer.toUnsignedLong(memory.getInt(4))),offset=8;
                while(offset+4<=end){
                    int type=Byte.toUnsignedInt(memory.getByte(offset)),length=Byte.toUnsignedInt(memory.getByte(offset+1));if(length<4||offset+length>end)break;
                    if(type==1&&length>=0x19){
                        byte[] bytes=memory.getByteArray(offset+8,16);boolean little=major>2||major==2&&minor>=6;
                        int[] order=little?new int[]{3,2,1,0,5,4,7,6,8,9,10,11,12,13,14,15}:new int[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15};
                        StringBuilder uuid=new StringBuilder(36);
                        for(int i=0;i<16;i++){if(i==4||i==6||i==8||i==10)uuid.append('-');int value=Byte.toUnsignedInt(bytes[order[i]]);uuid.append("0123456789ABCDEF".charAt(value>>>4)).append("0123456789ABCDEF".charAt(value&15));}
                        if(validUuid(uuid.toString()))return uuid.toString();
                    }
                    if(type==127)break;offset+=length;while(offset+1<end&&(memory.getByte(offset)!=0||memory.getByte(offset+1)!=0))offset++;offset+=2;
                }
            }
        }catch(Exception|LinkageError ignored){}
        return null;
    }
    private static String macUuid()throws IOException{
        Pointer key=null,value=null;int service=0;
        var foundation=NativeLibrary.getInstance("CoreFoundation");var io=NativeLibrary.getInstance("IOKit");
        try{
            var matching=io.getFunction("IOServiceMatching").invokePointer(new Object[]{"IOPlatformExpertDevice"});
            service=io.getFunction("IOServiceGetMatchingService").invokeInt(new Object[]{0,matching});if(service==0)throw new IOException("Mac platform service unavailable");
            key=foundation.getFunction("CFStringCreateWithCString").invokePointer(new Object[]{null,"IOPlatformUUID",0x08000100});
            value=io.getFunction("IORegistryEntryCreateCFProperty").invokePointer(new Object[]{service,key,null,0});
            if(value!=null)try(Memory buffer=new Memory(128)){
                if(foundation.getFunction("CFStringGetCString").invokeInt(new Object[]{value,buffer,new NativeLong(128),0x08000100})!=0){String uuid=buffer.getString(0,StandardCharsets.UTF_8.name()).toUpperCase(Locale.ROOT);if(validUuid(uuid))return uuid;}
            }
        }finally{
            if(value!=null)foundation.getFunction("CFRelease").invokeVoid(new Object[]{value});if(key!=null)foundation.getFunction("CFRelease").invokeVoid(new Object[]{key});if(service!=0)io.getFunction("IOObjectRelease").invokeInt(new Object[]{service});
        }
        throw new IOException("Mac hardware identifier unavailable");
    }
    private static String read(String file){try{return Files.readString(Path.of(file)).strip().toUpperCase(Locale.ROOT);}catch(IOException ignored){return "";}}
    private static boolean validUuid(String value){return value!=null&&value.matches("[A-F0-9]{8}-[A-F0-9]{4}-[A-F0-9]{4}-[A-F0-9]{4}-[A-F0-9]{12}")&&!value.replace("-","").matches("0+|F+");}
    public static void close(){detector.shutdownNow();}
}
