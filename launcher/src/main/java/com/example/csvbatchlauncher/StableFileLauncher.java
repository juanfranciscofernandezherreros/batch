package com.example.csvbatchlauncher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name="app.launcher.directory-scan-enabled", havingValue="true")
public class StableFileLauncher {
    private static final Logger log = LoggerFactory.getLogger(StableFileLauncher.class);
    private final LauncherProperties properties; private final StateStore state; private final BatchProcessExecutor executor; private final BatchReadinessStrategies readinessStrategies; private final Map<Path, Observation> observations = new ConcurrentHashMap<>();
    public StableFileLauncher(LauncherProperties p, StateStore s, BatchProcessExecutor e, BatchReadinessStrategies readinessStrategies) { properties=p; state=s; executor=e; this.readinessStrategies=readinessStrategies; }
    @EventListener(ApplicationReadyEvent.class) public void ready() { scan(); }
    @Scheduled(fixedDelayString="${app.launcher.poll-interval}") public void scan() {
        try { if(!Files.isDirectory(properties.watchDirectory())) return; try(var paths=Files.walk(properties.watchDirectory())) { paths.filter(Files::isRegularFile).filter(p->p.toString().toLowerCase().endsWith(".csv")).forEach(this::consider); } }
        catch(IOException e) { log.error("event=launcher_scan_failed directory={}",properties.watchDirectory(),e); }
    }
    private void consider(Path file) { var type=BatchType.from(file); boolean hasSupportedType=type.isPresent(); if(!hasSupportedType) return; try { var attr=Files.readAttributes(file,java.nio.file.attribute.BasicFileAttributes.class); var current=new Signature(attr.size(),attr.lastModifiedTime().toMillis()); var previous=observations.get(file); int stable=previous!=null&&previous.signature().equals(current)?previous.stableChecks()+1:1; observations.put(file,new Observation(current,stable)); boolean hasRequiredStability=stable>=properties.stabilityChecks(); if(!hasRequiredStability) return; String checksum=checksum(file); boolean wasCompleted=state.completed(file,checksum); if(wasCompleted) return; BatchReadiness readiness=readinessStrategies.assess(type.get(),file); boolean isReady=readiness.ready(); if(!isReady) { log.info("event=batch_process_blocked type={} file={} reason={}",type.get(),file,readiness.reason()); return; } log.info("event=batch_process_started type={} file={}",type.get(),file); boolean completedSuccessfully=executor.execute(type.get(),file); if(completedSuccessfully) state.markCompleted(file,checksum); } catch(IOException | RuntimeException e) { log.warn("event=launcher_file_failed file={}",file,e); } }
    private String checksum(Path file) throws IOException { try { var digest=MessageDigest.getInstance("SHA-256"); try(var in=new DigestInputStream(Files.newInputStream(file),digest)){in.transferTo(java.io.OutputStream.nullOutputStream());} return HexFormat.of().formatHex(digest.digest()); } catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);} }
    private record Signature(long size,long modified) {} private record Observation(Signature signature,int stableChecks) {}
}
