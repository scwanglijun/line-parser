package com.github.marschall.lineparser;

import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

final class CharArrayCharSequence implements CharSequence {

  private final char[] array;
  private String stringValue;

  CharArrayCharSequence(char[] array) {
    Objects.requireNonNull(array);
    this.array = array;
  }

  @Override
  public String toString() {
    // REVIEW caching the sting value pushes the object size from 16 to 24 bytes
    // maybe not worth it
    if (this.stringValue == null) {
      this.stringValue = new String(this.array);
    }
    return this.stringValue;
  }

  @Override
  public int length() {
    return this.array.length;
  }

  @Override
  public char charAt(int index) {
    return this.array[index];
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    if ((start < 0) || (start > this.array.length) || (start > end) || (end > this.array.length)) {
      throw new IndexOutOfBoundsException();
    }
    if (start == 0) {
      if (end == 0) {
        // avoid allocation
        return "";
      }
      return new CharArrayPrefixSubSequence(this.array, end);
    }

    int newLength = end - start;
    if (newLength == 0) {
      // avoid allocation
      return "";
    }
    return new CharArrayFullSubSequence(this.array, start, newLength);
  }

  @Override
  public IntStream chars() {
    return StreamSupport.intStream(new CharSequenceSpliterator(this), false);
  }

  // we use the default implementation for #codePoints because some chars
  // may be surrogates

}


final class CharArrayPrefixSubSequence implements CharSequence {

  private final char[] array;
  private final int count;
  private String stringValue;

  CharArrayPrefixSubSequence(char[] array, int length) {
    Objects.requireNonNull(array);
    this.array = array;
    this.count = length;
  }

  @Override
  public String toString() {
    if (this.stringValue == null) {
      this.stringValue = new String(this.array, 0, this.count);
    }
    return this.stringValue;
  }

  @Override
  public int length() {
    return this.count;
  }

  @Override
  public char charAt(int index) {
    if (index >= this.count) {
    throw new IndexOutOfBoundsException();
  }
    return this.array[index];
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    if ((start < 0) || (start > this.count) || (start > end) || (end > this.count)) {
      throw new IndexOutOfBoundsException();
    }
    if (start == 0) {
      if (end == 0) {
        // avoid allocation
        return "";
      }
      return new CharArrayPrefixSubSequence(this.array, end);
    }

    int newLength = end - start;
    if (newLength == 0) {
      // avoid allocation
      return "";
    }
    return new CharArrayFullSubSequence(this.array, start, newLength);
  }

  @Override
  public IntStream chars() {
    return StreamSupport.intStream(new CharSequenceSpliterator(this), false);
  }

  // we use the default implementation for #codePoints because some chars
  // may be surrogates

}


final class CharArrayFullSubSequence implements CharSequence {

  private final char[] array;
  private final int offset;
  private final int count;
  private String stringValue;

  CharArrayFullSubSequence(char[] array, int offset, int length) {
    Objects.requireNonNull(array);
    this.array = array;
    this.offset = offset;
    this.count = length;
  }

  @Override
  public String toString() {
    if (this.stringValue == null) {
      this.stringValue = new String(this.array, this.offset, this.count);
    }
    return this.stringValue;
  }

  @Override
  public int length() {
    return this.count;
  }

  @Override
  public char charAt(int index) {
    if ((index < 0) || (index >= this.count)) {
    throw new IndexOutOfBoundsException();
  }
    return this.array[this.offset + index];
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    if ((start < 0) || (start > this.count) || (start > end) || (end > this.count)) {
      throw new IndexOutOfBoundsException();
    }

    int newLength = end - start;
    if (newLength == 0) {
      // avoid allocation
      return "";
    }
    return new CharArrayFullSubSequence(this.array, this.offset + start, newLength);
  }

  @Override
  public IntStream chars() {
    return StreamSupport.intStream(new CharSequenceSpliterator(this), false);
  }

  // we use the default implementation for #codePoints because some chars
  // may be surrogates

}
