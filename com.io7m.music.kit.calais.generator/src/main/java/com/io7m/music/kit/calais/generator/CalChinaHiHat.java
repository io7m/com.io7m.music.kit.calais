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

import static com.io7m.music.kit.calais.generator.CalChinaHiHatOpennessKind.*;
import static com.io7m.music.kit.calais.generator.CalChinaHiHatStrikeKind.*;
import static com.io7m.music.kit.calais.generator.CalSnareStrikeKind.CROSS_STICK_STRIKE;
import static com.io7m.music.kit.calais.generator.CalSnareStrikeKind.HEAD_CENTER_STRIKE;
import static com.io7m.music.kit.calais.generator.CalSnareStrikeKind.HEAD_EDGE_STRIKE;
import static com.io7m.music.kit.calais.generator.CalSnareStrikeKind.RIM_SHOT;
import static com.io7m.music.kit.calais.generator.CalSnareStrikeKind.RIM_STRIKE;
import static com.io7m.music.kit.calais.generator.CalSnareTautnessKind.SNARES_LOOSE;
import static com.io7m.music.kit.calais.generator.CalSnareTautnessKind.SNARES_OFF;
import static com.io7m.music.kit.calais.generator.CalSnareTautnessKind.SNARES_TIGHT;

public final class CalChinaHiHat
{
  private static final Logger LOG =
    LoggerFactory.getLogger(CalChinaHiHat.class);

  private static final Pattern HIHAT_PATTERN =
    Pattern.compile(
      "([0-9]+)__quartertone__chh18x20-1-([a-z0-9]+)-v([0-9]+)\\.flac");

  private final EnumMap<CalChinaHiHatOpennessKind, CalChinaHiHatOpennessFiles> hiHats;

  public CalChinaHiHat(
    final EnumMap<CalChinaHiHatOpennessKind, CalChinaHiHatOpennessFiles> inHiHats)
  {
    this.hiHats = Objects.requireNonNull(inHiHats, "hiHats");
  }

  public static CalChinaHiHat open(
    final Path directory)
    throws IOException
  {
    final var hats =
      new EnumMap<CalChinaHiHatOpennessKind, CalChinaHiHatOpennessFiles>(
        CalChinaHiHatOpennessKind.class
      );

    try (var pathStream = Files.list(directory)) {
      final var files =
        pathStream
          .map(Path::toAbsolutePath)
          .sorted()
          .collect(Collectors.toList());

      for (final var file : files) {
        final var fileName = file.getFileName().toString();
        final var matcher = HIHAT_PATTERN.matcher(fileName);
        if (matcher.matches()) {
          final var type =
            matcher.group(2);
          final var velocity =
            matcher.group(3);

          LOG.info("{} {}", type, velocity);
          switch (type) {
            case "blc" -> {
              handleStrike(hats, file, CLOSED, BELL_STRIKE, velocity);
            }
            case "blho" -> {
              handleStrike(hats, file, HALF_OPEN, BELL_STRIKE, velocity);
            }
            case "blo" -> {
              handleStrike(hats, file, OPEN, BELL_STRIKE, velocity);
            }
            case "blsc" -> {
              handleStrike(hats, file, SEMI_CLOSED, BELL_STRIKE, velocity);
            }
            case "blso" -> {
              handleStrike(hats, file, SEMI_OPEN, BELL_STRIKE, velocity);
            }

            case "bwc" -> {
              handleStrike(hats, file, CLOSED, BOW_STRIKE, velocity);
            }
            case "bwho" -> {
              handleStrike(hats, file, HALF_OPEN, BOW_STRIKE, velocity);
            }
            case "bwo" -> {
              handleStrike(hats, file, OPEN, BOW_STRIKE, velocity);
            }
            case "bwsc" -> {
              handleStrike(hats, file, SEMI_CLOSED, BOW_STRIKE, velocity);
            }
            case "bwso" -> {
              handleStrike(hats, file, SEMI_OPEN, BOW_STRIKE, velocity);
            }

            case "ec" -> {
              handleStrike(hats, file, CLOSED, EDGE_STRIKE, velocity);
            }
            case "eho" -> {
              handleStrike(hats, file, HALF_OPEN, EDGE_STRIKE, velocity);
            }
            case "eo" -> {
              handleStrike(hats, file, OPEN, EDGE_STRIKE, velocity);
            }
            case "esc" -> {
              handleStrike(hats, file, SEMI_CLOSED, EDGE_STRIKE, velocity);
            }
            case "eso" -> {
              handleStrike(hats, file, SEMI_OPEN, EDGE_STRIKE, velocity);
            }

            case "ftchk" -> {

            }
            case "ftspl" -> {

            }

            default -> {
              throw new IllegalStateException(
                String.format("Unexpected value: %s", type)
              );
            }
          }
        }
      }

      return new CalChinaHiHat(hats);
    }
  }

  private static void handleStrike(
    final EnumMap<CalChinaHiHatOpennessKind, CalChinaHiHatOpennessFiles> hats,
    final Path file,
    final CalChinaHiHatOpennessKind openness,
    final CalChinaHiHatStrikeKind strike,
    final String velocity)
  {
    final var hatOpennessFiles =
      hats.computeIfAbsent(openness, k -> new CalChinaHiHatOpennessFiles());
    final var strikeFiles =
      hatOpennessFiles.filesByKind()
        .computeIfAbsent(strike, k -> new CalChinaHiHatStrikeFiles());
    final var byVelocity =
      strikeFiles.filesByVelocity();

    byVelocity.put(
      Integer.valueOf(Integer.valueOf(velocity).intValue() - 1),
      file
    );
  }

  public EnumMap<CalChinaHiHatOpennessKind, CalChinaHiHatOpennessFiles> hiHats()
  {
    return this.hiHats;
  }
}
