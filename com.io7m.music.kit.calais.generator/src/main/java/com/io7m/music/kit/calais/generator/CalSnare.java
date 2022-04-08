/*
 * Copyright © 2022 Mark Raynsford <code@io7m.com> https://www.io7m.com
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.io7m.music.kit.calais.generator.CalSnareStrikeKind.*;
import static com.io7m.music.kit.calais.generator.CalSnareTautnessKind.*;

public final class CalSnare
{
  private static final Logger LOG =
    LoggerFactory.getLogger(CalSnare.class);

  private static final Pattern SNARE_PATTERN =
    Pattern.compile(
      "([0-9]+)__quartertone__sd14x08tama-hp-([a-z0-9]+)-([a-z0-9]+)-v([0-9]+)\\.flac");

  private final EnumMap<CalSnareTautnessKind, CalSnareTautnessFiles> snare;

  public CalSnare(
    final EnumMap<CalSnareTautnessKind, CalSnareTautnessFiles> inSnare)
  {
    this.snare = Objects.requireNonNull(inSnare, "snare");
  }

  public static CalSnare open(
    final Path directory)
    throws IOException
  {
    final var snare =
      new EnumMap<CalSnareTautnessKind, CalSnareTautnessFiles>(
        CalSnareTautnessKind.class
      );

    try (var pathStream = Files.list(directory)) {
      final var files =
        pathStream
          .map(Path::toAbsolutePath)
          .sorted()
          .collect(Collectors.toList());

      for (final var file : files) {
        final var fileName = file.getFileName().toString();
        final var matcher = SNARE_PATTERN.matcher(fileName);
        if (matcher.matches()) {
          final var tautness =
            matcher.group(2);
          final var stick =
            matcher.group(3);
          final var velocity =
            matcher.group(4);

          LOG.info("{} {} {}", tautness, stick, velocity);
          switch (tautness) {
            case "0sn": {
              handleTautness(snare, file, SNARES_OFF, stick, velocity);
              break;
            }
            case "lsn": {
              handleTautness(snare, file, SNARES_LOOSE, stick, velocity);
              break;
            }
            case "tsn": {
              handleTautness(snare, file, SNARES_TIGHT, stick, velocity);
              break;
            }
            default: {
              throw new IllegalStateException(
                String.format("Unexpected value: %s", tautness)
              );
            }
          }
        }
      }

      return new CalSnare(snare);
    }
  }

  private static void handleTautness(
    final EnumMap<CalSnareTautnessKind, CalSnareTautnessFiles> snare,
    final Path file,
    final CalSnareTautnessKind tautness,
    final String stick,
    final String velocity)
  {
    switch (stick) {
      case "cs": {
        handleSnareStrike(snare, file, tautness, CROSS_STICK_STRIKE, velocity);
        break;
      }
      case "hdc": {
        handleSnareStrike(snare, file, tautness, HEAD_CENTER_STRIKE, velocity);
        break;
      }
      case "hde": {
        handleSnareStrike(snare, file, tautness, HEAD_EDGE_STRIKE, velocity);
        break;
      }
      case "rm": {
        handleSnareStrike(snare, file, tautness, RIM_STRIKE, velocity);
        break;
      }
      case "rs": {
        handleSnareStrike(snare, file, tautness, RIM_SHOT, velocity);
        break;
      }
      default: {
        throw new IllegalStateException(
          String.format("Unexpected value: %s", stick)
        );
      }
    }
  }

  private static void handleSnareStrike(
    final EnumMap<CalSnareTautnessKind, CalSnareTautnessFiles> snare,
    final Path file,
    final CalSnareTautnessKind tautness,
    final CalSnareStrikeKind strike,
    final String velocity)
  {
    final var tautnessFiles =
      snare.computeIfAbsent(tautness, k -> new CalSnareTautnessFiles());
    final var strikeFiles =
      tautnessFiles.filesByKind()
        .computeIfAbsent(strike, k -> new CalSnareStrikeFiles());
    final var byVelocity =
      strikeFiles.filesByVelocity();

    byVelocity.put(
      Integer.valueOf(Integer.valueOf(velocity).intValue() - 1),
      file
    );
  }

  public EnumMap<CalSnareTautnessKind, CalSnareTautnessFiles> snare()
  {
    return this.snare;
  }
}
