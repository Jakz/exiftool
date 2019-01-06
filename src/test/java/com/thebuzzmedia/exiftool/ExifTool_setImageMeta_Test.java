/**
 * Copyright 2011 The Buzz Media, LLC
 * Copyright 2015 Mickael Jeanroy <mickael.jeanroy@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thebuzzmedia.exiftool;

import static com.thebuzzmedia.exiftool.tests.MockitoUtils.anyListOf;
import static com.thebuzzmedia.exiftool.tests.MapUtils.newMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.rules.ExpectedException.none;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.thebuzzmedia.exiftool.core.StandardFormat;
import com.thebuzzmedia.exiftool.core.StandardTag;
import com.thebuzzmedia.exiftool.exceptions.UnwritableFileException;
import com.thebuzzmedia.exiftool.process.Command;
import com.thebuzzmedia.exiftool.process.CommandExecutor;
import com.thebuzzmedia.exiftool.process.CommandResult;
import com.thebuzzmedia.exiftool.process.OutputHandler;
import com.thebuzzmedia.exiftool.tests.builders.CommandResultBuilder;
import com.thebuzzmedia.exiftool.tests.builders.FileBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ExifTool_setImageMeta_Test {

	@Rule
	public ExpectedException thrown = none();

	private String path;

	@Mock
	private CommandExecutor executor;

	@Mock
	private ExecutionStrategy strategy;

	@Captor
	private ArgumentCaptor<List<String>> argsCaptor;

	private ExifTool exifTool;

	private Map<StandardTag, String> tags;

	@Before
	public void setUp() throws Exception {
		path = "exiftool";
		tags = newMap(
			StandardTag.APERTURE, "foo",
			StandardTag.ARTIST, "bar"
		);

		CommandResult result = new CommandResultBuilder()
			.output("9.36")
			.build();

		when(executor.execute(any(Command.class))).thenReturn(result);
		when(strategy.isSupported(any(Version.class))).thenReturn(true);

		exifTool = new ExifTool(path, executor, strategy);

		reset(executor);
	}

	@Test
	public void it_should_fail_if_image_is_null() throws Exception {
		thrown.expect(NullPointerException.class);
		thrown.expectMessage("Image cannot be null and must be a valid stream of image data.");
		exifTool.setImageMeta(null, StandardFormat.HUMAN_READABLE, tags);
	}

	@Test
	public void it_should_fail_if_format_is_null() throws Exception {
		thrown.expect(NullPointerException.class);
		thrown.expectMessage("Format cannot be null.");
		exifTool.setImageMeta(mock(File.class), null, tags);
	}

	@Test
	public void it_should_fail_if_tags_is_null() throws Exception {
		thrown.expect(NullPointerException.class);
		thrown.expectMessage("Tags cannot be null and must contain 1 or more Tag to query the image for.");
		exifTool.setImageMeta(mock(File.class), StandardFormat.HUMAN_READABLE, null);
	}

	@Test
	public void it_should_fail_if_tags_is_empty() throws Exception {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage("Tags cannot be null and must contain 1 or more Tag to query the image for.");
		exifTool.setImageMeta(mock(File.class), StandardFormat.HUMAN_READABLE, Collections.<Tag, String>emptyMap());
	}

	@Test
	public void it_should_fail_with_unknown_file() throws Exception {
		thrown.expect(UnwritableFileException.class);
		thrown.expectMessage("Unable to read the given image [/tmp/foo.png], ensure that the image exists at the given withPath and that the executing Java process has permissions to read it.");

		File image = new FileBuilder("foo.png")
			.exists(false)
			.build();

		exifTool.setImageMeta(image, StandardFormat.HUMAN_READABLE, tags);
	}

	@Test
	public void it_should_fail_with_non_writable_file() throws Exception {
		thrown.expect(UnwritableFileException.class);
		thrown.expectMessage("Unable to read the given image [/tmp/foo.png], ensure that the image exists at the given withPath and that the executing Java process has permissions to read it.");

		File image = new FileBuilder("foo.png")
			.canWrite(false)
			.build();

		exifTool.setImageMeta(image, StandardFormat.HUMAN_READABLE, tags);
	}

	@Test
	public void it_should_set_image_meta_data() throws Exception {
		final File image = new FileBuilder("foo.png").build();
		final Format format = StandardFormat.HUMAN_READABLE;

		doAnswer(new WriteTagsAnswer())
			.when(strategy).execute(same(executor), same(path), anyListOf(String.class), any(OutputHandler.class));

		exifTool.setImageMeta(image, format, tags);

		verify(strategy).execute(same(executor), same(path), argsCaptor.capture(), any(OutputHandler.class));

		List<String> args = argsCaptor.getValue();
		assertThat(args)
			.isNotEmpty()
			.isNotNull()
			.hasSize(5)
			.containsExactly(
				"-S",
				"-ApertureValue=foo",
				"-Artist=bar",
				"/tmp/foo.png",
				"-execute"
			);
	}

	@Test
	public void it_should_set_image_meta_data_in_numeric_format() throws Exception {
		final File image = new FileBuilder("foo.png").build();
		final Format format = StandardFormat.NUMERIC;

		doAnswer(new WriteTagsAnswer())
			.when(strategy).execute(same(executor), same(path), anyListOf(String.class), any(OutputHandler.class));

		exifTool.setImageMeta(image, format, tags);

		verify(strategy).execute(same(executor), same(path), argsCaptor.capture(), any(OutputHandler.class));

		List<String> args = argsCaptor.getValue();
		assertThat(args)
			.isNotEmpty()
			.isNotNull()
			.hasSize(6)
			.containsExactly(
				"-n",
				"-S",
				"-ApertureValue=foo",
				"-Artist=bar",
				"/tmp/foo.png",
				"-execute"
			);
	}

	@Test
	public void it_should_set_image_meta_data_in_numeric_format_by_default() throws Exception {
		final File image = new FileBuilder("foo.png").build();

		doAnswer(new WriteTagsAnswer())
			.when(strategy).execute(same(executor), same(path), anyListOf(String.class), any(OutputHandler.class));

		exifTool.setImageMeta(image, tags);

		verify(strategy).execute(same(executor), same(path), argsCaptor.capture(), any(OutputHandler.class));

		List<String> args = argsCaptor.getValue();
		assertThat(args)
			.isNotEmpty()
			.isNotNull()
			.hasSize(6)
			.containsExactly(
				"-n",
				"-S",
				"-ApertureValue=foo",
				"-Artist=bar",
				"/tmp/foo.png",
				"-execute"
			);
	}

	private static class WriteTagsAnswer implements Answer<String> {
		@Override
		public String answer(InvocationOnMock invocation) throws Throwable {
			return "{ready}";
		}
	}
}
