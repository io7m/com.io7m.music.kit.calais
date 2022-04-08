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

import com.io7m.jnoisetype.api.NTBankIndex;
import com.io7m.jnoisetype.api.NTGenerators;
import com.io7m.jnoisetype.api.NTGenericAmount;
import com.io7m.jnoisetype.api.NTInfo;
import com.io7m.jnoisetype.api.NTLongString;
import com.io7m.jnoisetype.api.NTPitch;
import com.io7m.jnoisetype.api.NTShortString;
import com.io7m.jnoisetype.api.NTTransforms;
import com.io7m.jnoisetype.api.NTVersion;
import com.io7m.jnoisetype.writer.api.NTBuilderProviderType;
import com.io7m.jnoisetype.writer.api.NTBuilderType;
import com.io7m.jnoisetype.writer.api.NTInstrumentBuilderType;
import com.io7m.jnoisetype.writer.api.NTSampleBuilderType;
import com.io7m.jnoisetype.writer.api.NTWriteException;
import com.io7m.jnoisetype.writer.api.NTWriterProviderType;
import com.io7m.jsamplebuffer.api.SampleBufferType;
import com.io7m.jsamplebuffer.vanilla.SampleBufferDouble;
import com.io7m.jsamplebuffer.xmedia.SampleBufferXMedia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

public final class CalFont
{
  private static final Logger LOG =
    LoggerFactory.getLogger(CalFont.class);

  private final NTBuilderProviderType builders;
  private final NTWriterProviderType writers;
  private final CalSnare snare;
  private final CalBassDrum bassDrum;
  private final CalChinaHiHat hiHats;
  private final CalZildjian18 crash;

  public CalFont(
    final NTBuilderProviderType inBuilders,
    final NTWriterProviderType inWriters,
    final CalSnare inSnare,
    final CalBassDrum inBassDrum,
    final CalChinaHiHat inHiHats,
    final CalZildjian18 inCrash)
  {
    this.builders =
      Objects.requireNonNull(inBuilders, "builders");
    this.writers =
      Objects.requireNonNull(inWriters, "writers");
    this.snare =
      Objects.requireNonNull(inSnare, "snare");
    this.bassDrum =
      Objects.requireNonNull(inBassDrum, "inBassDrum");
    this.hiHats =
      Objects.requireNonNull(inHiHats, "hiHats");
    this.crash =
      Objects.requireNonNull(inCrash, "crash");
  }

  public static CalFont of(
    final NTBuilderProviderType builders,
    final NTWriterProviderType writers,
    final CalSnare snare,
    final CalBassDrum bd,
    final CalChinaHiHat hiHats,
    final CalZildjian18 crash)
  {
    return new CalFont(
      builders,
      writers,
      snare,
      bd,
      hiHats,
      crash
    );
  }

  private record StereoSample(
    NTSampleBuilderType left,
    NTSampleBuilderType right)
  {
    StereoSample
    {
      Objects.requireNonNull(left, "left");
      Objects.requireNonNull(right, "right");
    }
  }

  /**
   * Add all of the snare samples.
   */

  private static void addSnareSampleDefinitions(
    final SortedMap<Integer, List<StereoSample>> snareSamples,
    final NTInstrumentBuilderType sfInstrument)
  {
    for (final var snareEntry : snareSamples.entrySet()) {
      final var rootNote =
        snareEntry.getKey().intValue();
      final var samples =
        snareEntry.getValue();

      final var velocityRegionSize = 128 / samples.size();
      var velocityLow = 0;
      var velocityHigh = velocityLow + velocityRegionSize;

      for (final var sample : samples) {
        final var zoneL = sfInstrument.addZone();
        zoneL.addKeyRangeGenerator(rootNote, rootNote);
        zoneL.addVelocityRangeGenerator(velocityLow, velocityHigh);
        zoneL.addGenerator(
          NTGenerators.findForName("sampleModes").orElseThrow(),
          NTGenericAmount.of(0));
        zoneL.addSampleGenerator(sample.left);

        // Hard pan left
        zoneL.addGenerator(
          NTGenerators.findForName("pan").orElseThrow(),
          NTGenericAmount.of(0xfe10)
        );

        final var zoneR = sfInstrument.addZone();
        zoneR.addKeyRangeGenerator(rootNote, rootNote);
        zoneR.addVelocityRangeGenerator(velocityLow, velocityHigh);
        zoneR.addGenerator(
          NTGenerators.findForName("sampleModes").orElseThrow(),
          NTGenericAmount.of(0));
        zoneR.addSampleGenerator(sample.right);

        // Hard pan right
        zoneR.addGenerator(
          NTGenerators.findForName("pan").orElseThrow(),
          NTGenericAmount.of(0x1f0)
        );

        velocityLow = velocityHigh + 1;
        velocityHigh = Math.min(127, velocityHigh + velocityRegionSize);
      }
    }
  }

  private static List<StereoSample> addSnareSpecific(
    final NTBuilderType builder,
    final int rootNote,
    final CalSnareTautnessKind tautnessKind,
    final CalSnareStrikeKind strikeKind,
    final CalSnareStrikeFiles strikeFiles)
    throws IOException
  {
    final var samples = new ArrayList<StereoSample>();
    for (final var entry : strikeFiles.filesByVelocity().entrySet()) {
      final var velocity = entry.getKey();
      final var file = entry.getValue();

      final var sampleNameL =
        String.format(
          "SNARE_%s_%s_%02d_L",
          tautnessKind.shortName(),
          strikeKind.shortName(),
          velocity
        );

      final var sampleNameR =
        String.format(
          "SNARE_%s_%s_%02d_R",
          tautnessKind.shortName(),
          strikeKind.shortName(),
          velocity
        );

      final var sampleL =
        builder.addSample(sampleNameL);
      final var sampleR =
        builder.addSample(sampleNameR);

      final SampleBufferType sampleBuffer;
      try (var stream =
             AudioSystem.getAudioInputStream(file.toFile())) {
        sampleBuffer =
          SampleBufferXMedia.sampleBufferOfStream(stream, CalFont::buffers);
      } catch (final UnsupportedAudioFileException e) {
        throw new IOException(e);
      }

      sampleL.setSampleRate((int) sampleBuffer.sampleRate());
      sampleL.setPitchCorrection(0);
      sampleL.setSampleCount(sampleBuffer.frames());
      sampleL.setOriginalPitch(NTPitch.of(rootNote));
      sampleL.setLoopStart(0L);
      sampleL.setLoopEnd(sampleBuffer.frames() - 1L);
      sampleL.setDataWriter(ch -> {
        copySampleToChannel(sampleBuffer, sampleNameL, ch, 0);
      });

      sampleR.setSampleRate((int) sampleBuffer.sampleRate());
      sampleR.setPitchCorrection(0);
      sampleR.setSampleCount(sampleBuffer.frames());
      sampleR.setOriginalPitch(NTPitch.of(rootNote));
      sampleR.setLoopStart(0L);
      sampleR.setLoopEnd(sampleBuffer.frames() - 1L);
      sampleR.setDataWriter(ch -> {
        copySampleToChannel(sampleBuffer, sampleNameL, ch, 1);
      });

      sampleR.setLinked(sampleL.sampleIndex());
      samples.add(new StereoSample(sampleL, sampleR));
    }
    return List.copyOf(samples);
  }

  /**
   * Add all of the bass drum samples.
   */

  private static void addBassDrumSampleDefinitions(
    final List<StereoSample> bassDrumSamples,
    final NTInstrumentBuilderType sfInstrument)
  {
    final var velocityRegionSize = 128 / bassDrumSamples.size();

    var velocityLow = 0;
    var velocityHigh = velocityLow + velocityRegionSize;

    for (final var bdSample : bassDrumSamples) {
      final var zoneL = sfInstrument.addZone();
      zoneL.addKeyRangeGenerator(24, 24);
      zoneL.addVelocityRangeGenerator(velocityLow, velocityHigh);
      zoneL.addGenerator(
        NTGenerators.findForName("decayVolEnv").orElseThrow(),
        NTGenericAmount.of(702)
      );
      zoneL.addGenerator(
        NTGenerators.findForName("sustainVolEnv").orElseThrow(),
        NTGenericAmount.of(1440)
      );
      zoneL.addGenerator(
        NTGenerators.findForName("sampleModes").orElseThrow(),
        NTGenericAmount.of(0));

      // Hard pan left
      zoneL.addGenerator(
        NTGenerators.findForName("pan").orElseThrow(),
        NTGenericAmount.of(0xfe10)
      );

      zoneL.addSampleGenerator(bdSample.left);

      final var zoneR = sfInstrument.addZone();
      zoneR.addKeyRangeGenerator(24, 24);
      zoneR.addVelocityRangeGenerator(velocityLow, velocityHigh);
      zoneR.addGenerator(
        NTGenerators.findForName("decayVolEnv").orElseThrow(),
        NTGenericAmount.of(702)
      );
      zoneR.addGenerator(
        NTGenerators.findForName("sustainVolEnv").orElseThrow(),
        NTGenericAmount.of(1440)
      );
      zoneR.addGenerator(
        NTGenerators.findForName("sampleModes").orElseThrow(),
        NTGenericAmount.of(0));

      // Hard pan right
      zoneR.addGenerator(
        NTGenerators.findForName("pan").orElseThrow(),
        NTGenericAmount.of(0x1f0)
      );

      zoneR.addSampleGenerator(bdSample.right);

      velocityLow = velocityHigh + 1;
      velocityHigh = Math.min(127, velocityHigh + velocityRegionSize);
    }
  }

  private List<StereoSample> addBassDrum(
    final NTBuilderType builder)
    throws IOException
  {
    final List<StereoSample> samples = new ArrayList<>();

    for (final var entry : this.bassDrum.byVelocity().entrySet()) {
      final var velocity = entry.getKey();
      final var file = entry.getValue();

      final var sampleNameL =
        String.format("BD_%02d_L", velocity);
      final var sampleNameR =
        String.format("BD_%02d_R", velocity);

      final var sampleL =
        builder.addSample(sampleNameL);
      final var sampleR =
        builder.addSample(sampleNameR);

      final SampleBufferType sampleBuffer;
      try (var stream =
             AudioSystem.getAudioInputStream(file.toFile())) {
        sampleBuffer =
          SampleBufferXMedia.sampleBufferOfStream(stream, CalFont::buffers);
      } catch (final UnsupportedAudioFileException e) {
        throw new IOException(e);
      }

      sampleL.setSampleRate((int) sampleBuffer.sampleRate());
      sampleL.setPitchCorrection(0);
      sampleL.setSampleCount(sampleBuffer.frames());
      sampleL.setOriginalPitch(NTPitch.of(24));
      sampleL.setLoopStart(0L);
      sampleL.setLoopEnd(sampleBuffer.frames() - 1L);
      sampleL.setDataWriter(ch -> {
        copySampleToChannel(sampleBuffer, sampleNameL, ch, 0);
      });

      sampleR.setSampleRate((int) sampleBuffer.sampleRate());
      sampleR.setPitchCorrection(0);
      sampleR.setSampleCount(sampleBuffer.frames());
      sampleR.setOriginalPitch(NTPitch.of(24));
      sampleR.setLoopStart(0L);
      sampleR.setLoopEnd(sampleBuffer.frames() - 1L);
      sampleR.setDataWriter(ch -> {
        copySampleToChannel(sampleBuffer, sampleNameL, ch, 1);
      });

      sampleR.setLinked(sampleL.sampleIndex());
      samples.add(new StereoSample(sampleL, sampleR));
    }
    return List.copyOf(samples);
  }

  private static SampleBufferType buffers(
    final int channels,
    final long frames,
    final double sampleRate)
  {
    return SampleBufferDouble.createWithHeapBuffer(
      channels,
      frames,
      sampleRate
    );
  }

  private static String textResource(
    final String name)
    throws IOException
  {
    final var path =
      String.format("/com/io7m/music/kit/calais/generator/%s", name);
    try (var stream = CalFont.class.getResourceAsStream(path)) {
      return new String(stream.readAllBytes(), US_ASCII);
    }
  }

  private static void copySampleToChannel(
    final SampleBufferType source,
    final String sampleName,
    final SeekableByteChannel channel,
    final int channelIndex)
    throws IOException
  {
    LOG.debug("copying: {}", sampleName);

    final var buffer =
      ByteBuffer.allocate(Math.toIntExact(source.frames() * 2L))
        .order(LITTLE_ENDIAN);

    final var frame = new double[source.channels()];
    for (var index = 0L; index < source.frames(); ++index) {
      source.frameGetExact(index, frame);

      final var frame_d = frame[channelIndex];
      final var frame_s = frame_d * 32767.0;
      final var frame_i = (short) frame_s;
      buffer.putShort(frame_i);
    }

    buffer.flip();
    final var wrote = channel.write(buffer);
    if (wrote != buffer.capacity()) {
      throw new IOException(
        new StringBuilder(32)
          .append("Wrote too few bytes (wrote ")
          .append(wrote)
          .append(" expected ")
          .append(buffer.capacity())
          .append(")")
          .toString()
      );
    }
  }

  public void write(
    final Path fileOutput)
    throws IOException
  {
    final var builder = this.builders.createBuilder();
    builder.setInfo(
      NTInfo.builder()
        .setName(NTShortString.of("Calais"))
        .setVersion(NTVersion.of(2, 11))
        .setProduct(NTShortString.of("com.io7m.music.kit.calais"))
        .setEngineers(NTShortString.of("Mark Raynsford <audio@io7m.com>"))
        .setCopyright(NTShortString.of(
          "(c) 2022 Mark Raynsford <audio@io7m.com>"))
        .setCreationDate(NTShortString.of("2022-04-06"))
        .setComment(NTLongString.of(textResource("comment.txt")))
        .build()
    );

    final var snareSamples =
      this.addSnare(builder);
    final var bdSamples =
      this.addBassDrum(builder);
    final var hiHatSamples =
      this.addHiHat(builder);
    final var crashSamples =
      this.addCrash(builder);

    instrumentWithVelocity(
      builder,
      snareSamples,
      bdSamples,
      hiHatSamples,
      crashSamples
    );

    this.serialize(fileOutput, builder);
  }

  private SortedMap<Integer, List<StereoSample>> addCrash(
    final NTBuilderType builder)
    throws IOException
  {
    final AtomicInteger rootNote =
      new AtomicInteger(60);
    final SortedMap<Integer, List<StereoSample>> samples =
      new TreeMap<>();

    try {
      this.crash.crash().forEach((strikeKind, strikeFiles) -> {
        try {
          final var rootNoteNow = rootNote.get();
          final var sampleList =
            addCrashSpecific(
              builder,
              rootNoteNow,
              strikeKind,
              strikeFiles
            );

          samples.put(Integer.valueOf(rootNoteNow), sampleList);
          rootNote.incrementAndGet();
        } catch (final IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    } catch (final UncheckedIOException e) {
      throw e.getCause();
    }
    return samples;
  }

  private static List<StereoSample> addCrashSpecific(
    final NTBuilderType builder,
    final int rootNote,
    final CalZildjian18StrikeKind strikeKind,
    final CalZildjian18StrikeFiles strikeFiles)
    throws IOException
  {
    final var samples = new ArrayList<StereoSample>();
    for (final var entry : strikeFiles.filesByVelocity().entrySet()) {
      final var velocity = entry.getKey();
      final var file = entry.getValue();

      final var sampleNameL =
        String.format(
          "CRASH_%s_%02d_L",
          strikeKind.shortName(),
          velocity
        );

      final var sampleNameR =
        String.format(
          "CRASH_%s_%02d_R",
          strikeKind.shortName(),
          velocity
        );

      final var sampleL =
        builder.addSample(sampleNameL);
      final var sampleR =
        builder.addSample(sampleNameR);

      final SampleBufferType sampleBuffer;
      try (var stream =
             AudioSystem.getAudioInputStream(file.toFile())) {
        sampleBuffer =
          SampleBufferXMedia.sampleBufferOfStream(stream, CalFont::buffers);
      } catch (final UnsupportedAudioFileException e) {
        throw new IOException(e);
      }

      sampleL.setSampleRate((int) sampleBuffer.sampleRate());
      sampleL.setPitchCorrection(0);
      sampleL.setSampleCount(sampleBuffer.frames());
      sampleL.setOriginalPitch(NTPitch.of(rootNote));
      sampleL.setLoopStart(0L);
      sampleL.setLoopEnd(sampleBuffer.frames() - 1L);
      sampleL.setDataWriter(ch -> {
        copySampleToChannel(sampleBuffer, sampleNameL, ch, 0);
      });

      sampleR.setSampleRate((int) sampleBuffer.sampleRate());
      sampleR.setPitchCorrection(0);
      sampleR.setSampleCount(sampleBuffer.frames());
      sampleR.setOriginalPitch(NTPitch.of(rootNote));
      sampleR.setLoopStart(0L);
      sampleR.setLoopEnd(sampleBuffer.frames() - 1L);
      sampleR.setDataWriter(ch -> {
        copySampleToChannel(sampleBuffer, sampleNameL, ch, 1);
      });

      sampleR.setLinked(sampleL.sampleIndex());
      samples.add(new StereoSample(sampleL, sampleR));
    }
    return List.copyOf(samples);
  }

  private SortedMap<Integer, List<StereoSample>> addHiHat(
    final NTBuilderType builder)
    throws IOException
  {
    final AtomicInteger rootNote =
      new AtomicInteger(42);
    final SortedMap<Integer, List<StereoSample>> samples =
      new TreeMap<>();

    try {
      this.hiHats.hiHats().forEach((opennessKind, opennessFiles) -> {
        opennessFiles.filesByKind().forEach((strikeKind, strikeFiles) -> {
          try {
            final var rootNoteNow = rootNote.get();
            final var sampleList =
              addHiHatSpecific(
                builder,
                rootNoteNow,
                opennessKind,
                strikeKind,
                strikeFiles
              );

            samples.put(Integer.valueOf(rootNoteNow), sampleList);
            rootNote.incrementAndGet();
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
        });
      });
    } catch (final UncheckedIOException e) {
      throw e.getCause();
    }
    return samples;
  }

  private static List<StereoSample> addHiHatSpecific(
    final NTBuilderType builder,
    final int rootNote,
    final CalChinaHiHatOpennessKind opennessKind,
    final CalChinaHiHatStrikeKind strikeKind,
    final CalChinaHiHatStrikeFiles strikeFiles)
    throws IOException
  {
    final var samples = new ArrayList<StereoSample>();
    for (final var entry : strikeFiles.filesByVelocity().entrySet()) {
      final var velocity = entry.getKey();
      final var file = entry.getValue();

      final var sampleNameL =
        String.format(
          "HH_%s_%s_%02d_L",
          opennessKind.shortName(),
          strikeKind.shortName(),
          velocity
        );

      final var sampleNameR =
        String.format(
          "HH_%s_%s_%02d_R",
          opennessKind.shortName(),
          strikeKind.shortName(),
          velocity
        );

      final var sampleL =
        builder.addSample(sampleNameL);
      final var sampleR =
        builder.addSample(sampleNameR);

      final SampleBufferType sampleBuffer;
      try (var stream =
             AudioSystem.getAudioInputStream(file.toFile())) {
        sampleBuffer =
          SampleBufferXMedia.sampleBufferOfStream(stream, CalFont::buffers);
      } catch (final UnsupportedAudioFileException e) {
        throw new IOException(e);
      }

      sampleL.setSampleRate((int) sampleBuffer.sampleRate());
      sampleL.setPitchCorrection(0);
      sampleL.setSampleCount(sampleBuffer.frames());
      sampleL.setOriginalPitch(NTPitch.of(rootNote));
      sampleL.setLoopStart(0L);
      sampleL.setLoopEnd(sampleBuffer.frames() - 1L);
      sampleL.setDataWriter(ch -> {
        copySampleToChannel(sampleBuffer, sampleNameL, ch, 0);
      });

      sampleR.setSampleRate((int) sampleBuffer.sampleRate());
      sampleR.setPitchCorrection(0);
      sampleR.setSampleCount(sampleBuffer.frames());
      sampleR.setOriginalPitch(NTPitch.of(rootNote));
      sampleR.setLoopStart(0L);
      sampleR.setLoopEnd(sampleBuffer.frames() - 1L);
      sampleR.setDataWriter(ch -> {
        copySampleToChannel(sampleBuffer, sampleNameL, ch, 1);
      });

      sampleR.setLinked(sampleL.sampleIndex());
      samples.add(new StereoSample(sampleL, sampleR));
    }
    return List.copyOf(samples);
  }

  private static void instrumentWithVelocity(
    final NTBuilderType builder,
    final SortedMap<Integer, List<StereoSample>> snareSamples,
    final List<StereoSample> bassDrumSamples,
    final SortedMap<Integer, List<StereoSample>> hiHatSamples,
    final SortedMap<Integer, List<StereoSample>> crashSamples)
  {
    final var sfInstrument =
      builder.addInstrument("calaisNoVelocity");

    final var preset =
      builder.addPreset(
        NTBankIndex.of(128),
        sfInstrument.name().value()
      );

    final var presetZoneGlobal =
      preset.addZone()
        .addKeyRangeGenerator(0, 127)
        .addInstrumentGenerator(sfInstrument);

    final var instrumentZoneGlobal =
      sfInstrument.addZone();

    /*
     * Allow for controlling the pitch via the pitch wheel.
     */

    instrumentZoneGlobal.addModulator(
      526,
      NTGenerators.findForName("coarseTune")
        .orElseThrow(() -> new IllegalStateException("Missing generator")),
      (short) 12,
      512,
      NTTransforms.find(0)
    );

    /*
     * Apply an inverse attenuation value based on the key velocity. This
     * means that the velocity curve is not linear, with notes at 0 velocity
     * still being audible.
     */

    instrumentZoneGlobal.addModulator(
      258,
      NTGenerators.findForName("initialAttenuation")
        .orElseThrow(() -> new IllegalStateException("Missing generator")),
      (short) -256,
      0,
      NTTransforms.find(0)
    );

    addSnareSampleDefinitions(snareSamples, sfInstrument);
    addBassDrumSampleDefinitions(bassDrumSamples, sfInstrument);
    addHiHatSampleDefinitions(hiHatSamples, sfInstrument);
    addCrashSampleDefinitions(crashSamples, sfInstrument);
  }

  private static void addCrashSampleDefinitions(
    final SortedMap<Integer, List<StereoSample>> crashSamples,
    final NTInstrumentBuilderType sfInstrument)
  {
    for (final var crashEntry : crashSamples.entrySet()) {
      final var rootNote =
        crashEntry.getKey().intValue();
      final var samples =
        crashEntry.getValue();

      final var velocityRegionSize = 128 / samples.size();
      var velocityLow = 0;
      var velocityHigh = velocityLow + velocityRegionSize;

      for (final var sample : samples) {
        final var zoneL = sfInstrument.addZone();
        zoneL.addKeyRangeGenerator(rootNote, rootNote);
        zoneL.addVelocityRangeGenerator(velocityLow, velocityHigh);
        zoneL.addGenerator(
          NTGenerators.findForName("sampleModes").orElseThrow(),
          NTGenericAmount.of(0));
        zoneL.addSampleGenerator(sample.left);

        // Hard pan left
        zoneL.addGenerator(
          NTGenerators.findForName("pan").orElseThrow(),
          NTGenericAmount.of(0xfe10)
        );

        final var zoneR = sfInstrument.addZone();
        zoneR.addKeyRangeGenerator(rootNote, rootNote);
        zoneR.addVelocityRangeGenerator(velocityLow, velocityHigh);
        zoneR.addGenerator(
          NTGenerators.findForName("sampleModes").orElseThrow(),
          NTGenericAmount.of(0));
        zoneR.addSampleGenerator(sample.right);

        // Hard pan right
        zoneR.addGenerator(
          NTGenerators.findForName("pan").orElseThrow(),
          NTGenericAmount.of(0x1f0)
        );

        velocityLow = velocityHigh + 1;
        velocityHigh = Math.min(127, velocityHigh + velocityRegionSize);
      }
    }
  }

  private static void addHiHatSampleDefinitions(
    final SortedMap<Integer, List<StereoSample>> hiHatSamples,
    final NTInstrumentBuilderType sfInstrument)
  {
    for (final var hiHatEntry : hiHatSamples.entrySet()) {
      final var rootNote =
        hiHatEntry.getKey().intValue();
      final var samples =
        hiHatEntry.getValue();

      final var velocityRegionSize = 128 / samples.size();
      var velocityLow = 0;
      var velocityHigh = velocityLow + velocityRegionSize;

      for (final var sample : samples) {
        final var zoneL = sfInstrument.addZone();
        zoneL.addKeyRangeGenerator(rootNote, rootNote);
        zoneL.addVelocityRangeGenerator(velocityLow, velocityHigh);
        zoneL.addGenerator(
          NTGenerators.findForName("sampleModes").orElseThrow(),
          NTGenericAmount.of(0));
        zoneL.addSampleGenerator(sample.left);

        // Hard pan left
        zoneL.addGenerator(
          NTGenerators.findForName("pan").orElseThrow(),
          NTGenericAmount.of(0xfe10)
        );

        final var zoneR = sfInstrument.addZone();
        zoneR.addKeyRangeGenerator(rootNote, rootNote);
        zoneR.addVelocityRangeGenerator(velocityLow, velocityHigh);
        zoneR.addGenerator(
          NTGenerators.findForName("sampleModes").orElseThrow(),
          NTGenericAmount.of(0));
        zoneR.addSampleGenerator(sample.right);

        // Hard pan right
        zoneR.addGenerator(
          NTGenerators.findForName("pan").orElseThrow(),
          NTGenericAmount.of(0x1f0)
        );

        velocityLow = velocityHigh + 1;
        velocityHigh = Math.min(127, velocityHigh + velocityRegionSize);
      }
    }
  }

  private void serialize(
    final Path fileOutput,
    final NTBuilderType builder)
    throws IOException
  {
    final var description = builder.build();
    try (var channel =
           FileChannel.open(fileOutput, CREATE, TRUNCATE_EXISTING, WRITE)) {
      final var writer =
        this.writers.createForChannel(fileOutput.toUri(), description, channel);
      writer.write();
    } catch (final NTWriteException e) {
      throw new IOException(e);
    }
  }

  private SortedMap<Integer, List<StereoSample>> addSnare(
    final NTBuilderType builder)
    throws IOException
  {
    final AtomicInteger rootNote =
      new AtomicInteger(36);
    final SortedMap<Integer, List<StereoSample>> samples =
      new TreeMap<>();

    try {
      this.snare.snare().forEach((tautnessKind, tautnessFiles) -> {
        tautnessFiles.filesByKind().forEach((strikeKind, strikeFiles) -> {
          try {
            final var rootNoteNow = rootNote.get();
            final var sampleList =
              addSnareSpecific(
                builder,
                rootNoteNow,
                tautnessKind,
                strikeKind,
                strikeFiles
              );

            samples.put(Integer.valueOf(rootNoteNow), sampleList);
            rootNote.incrementAndGet();
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
        });
      });
    } catch (final UncheckedIOException e) {
      throw e.getCause();
    }
    return samples;
  }
}
