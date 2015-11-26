package com.github.marschall.lineparser;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Parses a file into multiple lines.
 *
 * <p>Intended for cases where:</p>
 * <ul>
 *  <li>the start position in the file of a line is required</li>
 *  <li>the length in bytes of a line is required</li>
 *  <li>only a few character of every line is required</li>
 * </ul>
 *
 * @see Line
 */
public final class LineParser {

  private static final byte[] CR_LF = {'\r', '\n'};

  private static final byte[] CR = {'\r'};

  private static final byte[] LF = {'\n'};

  private final int maxMapSize;

  public LineParser() {
    this(Integer.MAX_VALUE);
  }

  LineParser(int maxBufferSize) {
    this.maxMapSize = maxBufferSize;
  }

  /**
   * Internal iterator over every line in a file.
   *
   * @param path the file to parse
   * @param cs the character set to use
   * @param lineCallback callback executed for every line
   * @throws IOException if an exception happens when reading
   */
  public void forEach(Path path, Charset cs, Consumer<Line> lineCallback) throws IOException {
    try (FileInputStream stream = new FileInputStream(path.toFile());
            FileChannel channel = stream.getChannel()) {
      long fileSize = channel.size();
      forEach(channel, cs, fileSize, 0L, lineCallback);
    }
  }

  private void forEach(FileChannel channel, Charset cs, long fileSize, long mapStart, Consumer<Line> lineCallback) throws IOException {
    int mapSize = (int) Math.min(fileSize - mapStart, maxMapSize);
    MappedByteBuffer buffer = channel.map(MapMode.READ_ONLY, mapStart, mapSize);
    try {
      CharBuffer charBuffer = CharBuffer.allocate(2048);
      CharsetDecoder decoder = cs.newDecoder();

      int lineStart = 0; // in buffer
      byte[] lf = "\n".getBytes(cs);
      byte[] cr = "\r".getBytes(cs);
      byte[] crlf = "\r\n".getBytes(cs);

      int mapIndex = 0;
      scanloop: while (mapIndex < mapSize) {
        byte value = buffer.get();

        if (value == cr[0] && cr.length - 1 <= buffer.remaining()) {
          // input starts with the first byte of a cr, but cr may be multiple bytes
          // check if the input starts with all bytes of a cr
          for (int j = 1; j < cr.length; j++) {
            if (buffer.get() != cr[j]) {
              // wasn't a cr after all
              // the the buffer state and loop variable
              buffer.position(mapIndex + 1);
              mapIndex += 1;
              continue scanloop;
            }
          }

          byte[] newline = cr;
          // check if lf follows the cr
          crlftest: if (lf.length <= buffer.remaining()) {
            for (int j = 0; j < lf.length; j++) {
              if (buffer.get() != lf[j]) {
                // not a lf
                // be don't need to fix the buffer state here
                // having the information that the newline is just a cr is enough
                // to make the read and fix code work later
                break crlftest;
              }
            }
            newline = crlf;
          }

          // read the current line into a CharSequence
          // create a Line object
          // call the callback
          buffer.position(lineStart).limit(mapIndex);
          charBuffer = decode(buffer.slice(), charBuffer, decoder);
          Line line = new Line(lineStart + mapStart, mapIndex - lineStart, charBuffer);
          lineCallback.accept(line);

          // fix up the buffer and loop variable for the next iteration
          buffer.limit(buffer.capacity());
          lineStart = mapIndex + newline.length;
          buffer.position(lineStart);
          mapIndex = lineStart;

        } else if (value == lf[0] && lf.length - 1 <= buffer.remaining()) {
          // input starts with the first byte of a lf, but lf may be multiple bytes
          // check if the input starts with all bytes of a lf
          for (int j = 1; j < lf.length; j++) {
            if (buffer.get() != lf[j]) {
              // wasn't a lf after all
              // the the buffer state and loop variable
              buffer.position(mapIndex + 1);
              mapIndex += 1;
              continue scanloop;
            }
          }

          // read the current line into a CharSequence
          // create a Line object
          // call the callback
          buffer.position(lineStart).limit(mapIndex);
          charBuffer = decode(buffer.slice(), charBuffer, decoder);
          Line line = new Line(lineStart + mapStart, mapIndex - lineStart, charBuffer);
          lineCallback.accept(line);

          // fix up the buffer and loop variable for the next iteration
          buffer.limit(buffer.capacity());
          lineStart = mapIndex + lf.length;
          buffer.position(lineStart);
          mapIndex = lineStart;
        } else {
          mapIndex += 1;
        }

      }

      if (mapSize + mapStart < fileSize) {
        // not the last mapping
        // TODO we should unmap now
        forEach(channel, cs, fileSize, mapStart + lineStart, lineCallback); // may result in overlapping mapping
      } else if (lineStart < mapSize) {
        // if the last line didn't end in a newline read it now
        buffer.position(lineStart);
        charBuffer = decode(buffer.slice(), charBuffer, decoder);
        Line line = new Line(lineStart + mapStart, mapSize - lineStart, charBuffer);
        lineCallback.accept(line);
      }

    } finally {
      unmap(buffer);
    }
  }

  private static CharBuffer decode(ByteBuffer in, CharBuffer out, CharsetDecoder decoder) {
    in.rewind();
    out.rewind();
    CoderResult result = decoder.decode(in, out, true);
    if (result.isOverflow()) {
      int newCapacity = out.capacity() * 2;
      return decode(in, CharBuffer.allocate(newCapacity), decoder);
    } else {
      out.flip();
      return out;
    }
  }

  private static void unmap(MappedByteBuffer buffer) {
    try {
      Method cleanerMethod = buffer.getClass().getMethod("cleaner");
      if (!cleanerMethod.isAccessible()) {
        cleanerMethod.setAccessible(true);
      }
      Object cleaner = cleanerMethod.invoke(buffer);
      Method cleanMethod = cleaner.getClass().getMethod("clean");
      if (!cleanMethod.isAccessible()) {
        cleanMethod.setAccessible(true);
      }
      cleanMethod.invoke(cleaner);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("could not unmap buffer", e);
    }
//    sun.misc.Cleaner cleaner = ((sun.nio.ch.DirectBuffer) buffer).cleaner();
//    cleaner.clean();
  }

}
