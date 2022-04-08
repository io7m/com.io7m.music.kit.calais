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

import static com.io7m.music.kit.calais.generator.CalSnareStrikeKind.CROSS_STICK_STRIKE;
import static com.io7m.music.kit.calais.generator.CalSnareStrikeKind.HEAD_CENTER_STRIKE;
import static com.io7m.music.kit.calais.generator.CalSnareStrikeKind.HEAD_EDGE_STRIKE;
import static com.io7m.music.kit.calais.generator.CalSnareStrikeKind.RIM_SHOT;
import static com.io7m.music.kit.calais.generator.CalSnareStrikeKind.RIM_STRIKE;
import static com.io7m.music.kit.calais.generator.CalSnareTautnessKind.SNARES_LOOSE;
import static com.io7m.music.kit.calais.generator.CalSnareTautnessKind.SNARES_OFF;
import static com.io7m.music.kit.calais.generator.CalSnareTautnessKind.SNARES_TIGHT;
import static com.io7m.music.kit.calais.generator.CalZildjian18StrikeKind.BELL_STRIKE;
import static com.io7m.music.kit.calais.generator.CalZildjian18StrikeKind.BOW_STRIKE;
import static com.io7m.music.kit.calais.generator.CalZildjian18StrikeKind.EDGE_STRIKE;

public final class CalZildjian18
{
  private static final Logger LOG =
    LoggerFactory.getLogger(CalZildjian18.class);

  private static final Pattern SNARE_PATTERN =
    Pattern.compile(
      "([0-9]+)__quartertone__cc18zm-([a-z0-9]+)-v([0-9]+)\\.flac");

  private final EnumMap<CalZildjian18StrikeKind, CalZildjian18StrikeFiles> crash;

  public CalZildjian18(
    final EnumMap<CalZildjian18StrikeKind, CalZildjian18StrikeFiles> inCrash)
  {
    this.crash = Objects.requireNonNull(inCrash, "crash");
  }

  public static CalZildjian18 open(
    final Path directory)
    throws IOException
  {
    final var crash =
      new EnumMap<CalZildjian18StrikeKind, CalZildjian18StrikeFiles>(
        CalZildjian18StrikeKind.class
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
          final var strike =
            matcher.group(2);
          final var velocity =
            matcher.group(3);

          LOG.info("{} {}", strike, velocity);
          switch (strike) {
            case "bw": {
              handleStrike(crash, file, BOW_STRIKE, velocity);
              break;
            }
            case "bl": {
              handleStrike(crash, file, BELL_STRIKE, velocity);
              break;
            }
            case "ed": {
              handleStrike(crash, file, EDGE_STRIKE, velocity);
              break;
            }
            default: {
              throw new IllegalStateException(
                String.format("Unexpected value: %s", strike)
              );
            }
          }
        }
      }

      return new CalZildjian18(crash);
    }
  }

  private static void handleStrike(
    final EnumMap<CalZildjian18StrikeKind, CalZildjian18StrikeFiles> crash,
    final Path file,
    final CalZildjian18StrikeKind strike,
    final String velocity)
  {
    final var strikeFiles =
      crash.computeIfAbsent(strike, k -> new CalZildjian18StrikeFiles());
    final var byVelocity =
      strikeFiles.filesByVelocity();

    byVelocity.put(
      Integer.valueOf(Integer.valueOf(velocity).intValue() - 1),
      file
    );
  }

  public EnumMap<CalZildjian18StrikeKind, CalZildjian18StrikeFiles> crash()
  {
    return this.crash;
  }
}
