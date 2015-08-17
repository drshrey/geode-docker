package com.gemstone.gemfire.cache.lucene.internal;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;


import com.gemstone.gemfire.cache.Region;

public class FileSystem {
  // private final Cache cache;
  private final Region<String, File> fileRegion;
  private final Region<ChunkKey, byte[]> chunkRegion;
  
  final int chunkSize = 1_000_000;

  public FileSystem(Region<String, File> fileRegion, Region<ChunkKey, byte[]> chunkRegion) {
    super();
    this.fileRegion = fileRegion;
    this.chunkRegion = chunkRegion;
  }

  public Collection<String> listFileNames() {
    return fileRegion.keySet();
  }

  public File createFile(final String name) throws IOException {
    // TODO lock region ?
    final File file = new File(this, name);
    if (null != fileRegion.putIfAbsent(name, file)) {
      throw new IOException("File exists.");
    }
    // TODO unlock region ?
    return file;
  }
  
  public File getFile(final String name) throws FileNotFoundException {
    final File file = fileRegion.get(name);
    
    if (null == file) {
      throw new FileNotFoundException(name);
    }
    
    file.setFileSystem(this);
    return file;
  }

  public void deleteFile(final String name) {
    // TODO locks?

    // TODO consider removeAll with all ChunkKeys listed.
    final ChunkKey key = new ChunkKey(name, 0);
    while (true) {
      // TODO consider mutable ChunkKey
      if (null == chunkRegion.remove(key)) {
        // no more chunks
        break;
      }
      key.chunkId++;
    }
    
    fileRegion.remove(name);
  }

  public void renameFile(String source, String dest) throws IOException {
    final File destFile = createFile(dest);
    
    final File sourceFile = fileRegion.remove(source);
    if (null == sourceFile) {
      throw new FileNotFoundException(source);
    }

    destFile.chunks = sourceFile.chunks;
    destFile.created = sourceFile.created;
    destFile.length = sourceFile.length;
    destFile.modified = sourceFile.modified;

    // TODO copy on write?
    final ChunkKey sourceKey = new ChunkKey(source, 0);
    while (true) {
      byte[] chunk = chunkRegion.remove(sourceKey);
      if (null == chunk) {
        // no more chunks
        break;
      }
      putChunk(destFile, sourceKey.chunkId, chunk);
      sourceKey.chunkId++;
    }
    
    updateFile(destFile);
  }
  
  byte[] getChunk(final File file, final int id) {
    final ChunkKey key = new ChunkKey(file.getName(), id);
    final byte[] chunk = chunkRegion.get(key);
    return chunk;
  }

  public void putChunk(final File file, final int id, final byte[] chunk) {
    final ChunkKey key = new ChunkKey(file.getName(), id);
    chunkRegion.put(key, chunk);
  }

  void updateFile(File file) {
    fileRegion.put(file.getName(), file);
  }

  


}
