package com.sora.util.akatsuki.compiler;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.sora.util.akatsuki.compiler.InMemoryJavaFileManager.InMemoryJavaFileObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.annotation.processing.Processor;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import static com.google.common.base.Charsets.UTF_8;

public class CompilerUtils {

	static Result compile(ClassLoader loader, Iterable<Processor> processors,
			JavaFileObject... objects) {
		// we need all this because we got a annotation processor, the generated
		// class has to go into memory too
		final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
		InMemoryJavaFileManager fileManager = new InMemoryJavaFileManager(
				compiler.getStandardFileManager(diagnosticCollector, Locale.getDefault(), UTF_8),
				loader);
		CompilationTask task = compiler.getTask(null, fileManager, diagnosticCollector, null,
				ImmutableSet.of(), Arrays.asList(objects));
		task.setProcessors(processors);
		if (!task.call()) {
			throw new RuntimeException(
					"Compilation failed:\n" + printVertically(diagnosticCollector.getDiagnostics())
							+ " Input sources:\n" + printAllSources(fileManager.getOutputFiles()));
		}
		return new Result(fileManager.getClassLoader(StandardLocation.CLASS_OUTPUT),
				fileManager.getOutputFiles());
	}

	static String printVertically(List<?> collection) {
		return Joiner.on("\n").join(collection);
	}

	static String printAllSources(List<JavaFileObject> sources) {
		final List<InMemoryJavaFileObject> sourceFiles = sources.stream()
				.filter(f -> f instanceof InMemoryJavaFileObject)
				.map(f -> (InMemoryJavaFileObject) f).filter(InMemoryJavaFileObject::isSource)
				.collect(Collectors.toList());
		final StringWriter writer = new StringWriter();
		writer.append("\nGenerated source(s):\n");
		for (InMemoryJavaFileObject file : sourceFiles) {
			writer.append("File:").append(file.toUri().toString()).append("\n");
			try {
				file.printSource(writer);
			} catch (IOException e) {
				// what else can we do?
				writer.append("An exception occurred while trying to print the source:\n");
				e.printStackTrace(new PrintWriter(writer));
			}
			writer.append("\n===============================\n");
		}
		return writer.toString();
	}

	static class Result {

		public final ClassLoader classLoader;
		public final ImmutableList<JavaFileObject> sources;

		public Result(ClassLoader classLoader, ImmutableList<JavaFileObject> sources) {
			this.classLoader = classLoader;
			this.sources = sources;
		}

		public String printAllSources() {
			return CompilerUtils.printAllSources(sources);
		}
	}

}
