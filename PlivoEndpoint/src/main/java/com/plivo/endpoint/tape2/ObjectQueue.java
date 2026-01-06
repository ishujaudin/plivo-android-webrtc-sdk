// Copyright 2011 Square, Inc.
package com.plivo.endpoint.tape2;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;

import org.json.JSONArray;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** A queue of objects. */
public abstract class ObjectQueue<T> implements Iterable<T>, Closeable {
  /** A queue for objects that are atomically and durably serialized to {@code file}. */
  public static <T> ObjectQueue<T> create(QueueFile qf) {
    return new FileObjectQueue<>(qf);
  }


  /** The underlying {@link QueueFile} backing this queue, or null if it's only in memory. */
  public abstract @Nullable
  QueueFile file();

  /** Returns the number of entries in the queue. */
  public abstract int size();

  /** Returns {@code true} if this queue contains no entries. */
  public boolean isEmpty() {
    return size() == 0;
  }

  /** Enqueues an entry that can be processed at any time. */
  public abstract void add(T entry) throws IOException;

  /**
   * Returns the head of the queue, or {@code null} if the queue is empty. Does not modify the
   * queue.
   */
  public abstract @Nullable byte[] peek() throws IOException;

  /**
   * Reads up to {@code max} entries from the head of the queue without removing the entries.
   * If the queue's {@link #size()} is less than {@code max} then only {@link #size()} entries
   * are read.
   */
  public Pair<Boolean, JSONArray> peek(int max) throws IOException {
    int end = Math.min(max, size());
    JSONArray jsonArray = new JSONArray();
    int sizeCount = 0;
    for (int i = 0; i < end; i++) {
      byte[] entry = this.peek();
      if(entry!=null){
        sizeCount += entry.length;
        if(sizeCount > 19000){
          jsonArray.put("...continued");
          return new Pair<>(true, jsonArray);
        }
        String logEntry = getUTF8fromByte(entry);
        if(!logEntry.isEmpty()) {
          jsonArray.put(getUTF8fromByte(entry));
        }
      }
      this.remove();
    }
    return new Pair<>(false, jsonArray);
  }

  private String getUTF8fromByte(byte[] entry) {
    return new String(entry, StandardCharsets.UTF_8);
  }


  /** Returns the entries in the queue as an unmodifiable {@link List}. */
  public Pair<Boolean, JSONArray> asList() throws IOException {
    return peek(size());
  }

  /** Removes the head of the queue. */
  public void remove() throws IOException {
    remove(1);
  }

  /** Removes {@code n} entries from the head of the queue. */
  public abstract void remove(int n) throws IOException;

  /** Clears this queue. Also truncates the file to the initial size. */
  public abstract void clear() throws IOException;

  /**
   * Convert a byte stream to and from a concrete type.
   *
   * @param <T> Object type.
   */
  public interface Converter<T> {
    /** Converts bytes to an object. */
    T from(byte[] source) throws IOException;

    /** Converts {@code value} to bytes written to the specified stream. */
    void toStream(T value, OutputStream sink) throws IOException;
  }
}
