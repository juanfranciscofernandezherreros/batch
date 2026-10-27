package com.example.csvbatchlauncher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StateStore {
    private final Path file; private final Map<String, String> entries = new HashMap<>();
    public StateStore(LauncherProperties properties) { file = properties.stateFile(); load(); }
    public synchronized boolean completed(Path path, String checksum) { return checksum.equals(entries.get(path.toAbsolutePath().normalize().toString())); }
    public synchronized void markCompleted(Path path, String checksum) { entries.put(path.toAbsolutePath().normalize().toString(), checksum); persist(); }
    private void load() { try { if (Files.exists(file)) for (String line : Files.readAllLines(file)) { int i=line.indexOf('='); if(i>0) entries.put(line.substring(0,i),line.substring(i+1)); } } catch(IOException e) { throw new IllegalStateException("Cannot read launcher state",e); } }
    private void persist() { try { if(file.getParent()!=null) Files.createDirectories(file.getParent()); Files.write(file, entries.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList()); } catch(IOException e) { throw new IllegalStateException("Cannot write launcher state",e); } }
}
