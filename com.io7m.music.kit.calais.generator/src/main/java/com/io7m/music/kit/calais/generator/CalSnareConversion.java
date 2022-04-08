/*
 * Copyright © 2021 Mark Raynsford <code@io7m.com> http://io7m.com
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package com.io7m.music.kit.calais.generator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;

import static com.io7m.music.kit.calais.generator.CalSnareTautnessKind.SNARES_TIGHT;

public final class CalSnareConversion
{
  private static final Logger LOG =
    LoggerFactory.getLogger(CalSnareConversion.class);

  private CalSnareConversion()
  {

  }

  public static CalSnare convertFLACs(
    final CalSnare snareInput,
    final Path outputDirectory)
    throws IOException
  {
    final var snareOutput =
      new EnumMap<CalSnareTautnessKind, CalSnareTautnessFiles>(
        CalSnareTautnessKind.class
      );

    try {
      final var tautnessFiles =
        snareInput.snare()
          .get(SNARES_TIGHT);

      tautnessFiles.filesByKind().forEach((strikeKind, strikeFiles) -> {
        strikeFiles.filesByVelocity().forEach((velocity, path) -> {
          try {
            convert(
              snareOutput,
              SNARES_TIGHT,
              strikeKind,
              velocity,
              path,
              outputDirectory
            );
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
        });
      });
    } catch (final UncheckedIOException e) {
      throw e.getCause();
    }

    return new CalSnare(snareOutput);
  }

  private static void convert(
    final EnumMap<CalSnareTautnessKind, CalSnareTautnessFiles> snareOutput,
    final CalSnareTautnessKind tautnessKind,
    final CalSnareStrikeKind strikeKind,
    final Integer velocity,
    final Path path,
    final Path outputDirectory)
    throws IOException
  {
    final var outputFileDirectory =
      outputDirectory.resolve(tautnessKind.name())
        .resolve(strikeKind.name());
    final var outputFile =
      outputFileDirectory.resolve(
        String.format("%02d.wav", velocity));

    Files.createDirectories(outputFileDirectory);

    LOG.info("write {}", outputFile);

    try (var stream = CalFLAC.readAs16(path)) {
      AudioSystem.write(stream, AudioFileFormat.Type.WAVE, outputFile.toFile());
    } catch (final UnsupportedAudioFileException e) {
      throw new IOException(e);
    }

    final var tautnessFiles =
      snareOutput.computeIfAbsent(tautnessKind, k -> new CalSnareTautnessFiles());
    final var strikeFiles =
      tautnessFiles.filesByKind()
        .computeIfAbsent(strikeKind, k -> new CalSnareStrikeFiles());
    final var byVelocity =
      strikeFiles.filesByVelocity();

    byVelocity.put(
      velocity,
      outputFile
    );
  }
}
