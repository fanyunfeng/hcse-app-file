package com.hcse.file.app;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.util.DefaultPrettyPrinter;

import com.hcse.file.ListFile;
import com.hcse.file.Record;
import com.hcse.file.RecordHandler;
import com.hcse.file.RecordInputStream;
import com.hcse.file.app.common.ExitExeption;
import com.hcse.file.util.Field;

enum DocContentFormat {
    array, object
};

public class DataFile2Json {
    protected static final Logger logger = Logger.getLogger(DataFile2Json.class);

    private boolean listFile = false;

    private String filename;
    private String outputFilename;

    private String directory;

    private String intputCharset;
    private String outputCharset;

    private boolean pretty = true;
    private DocContentFormat mode = DocContentFormat.object;

    private boolean outputToOneFile = false;
    // private RecordOutputStream outputStream;

    private OutputStream outputStream;

    private ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("static-access")
    protected void init(Options options) throws ExitExeption {
        options.addOption(OptionBuilder.withLongOpt("file").withDescription("file name.").hasArg().withArgName("file")
                .create('f'));

        options.addOption(OptionBuilder.withLongOpt("output").withDescription("output file name.").hasArg()
                .withArgName("output").create('o'));

        options.addOption(OptionBuilder.withLongOpt("list").withDescription("file is list file name.").hasArg()
                .withArgName("list").create('l'));

        options.addOption(OptionBuilder.withLongOpt("pretty").withDescription("print pretty format. true/false")
                .hasArg().withArgName("pretty").create());

        options.addOption(OptionBuilder.withLongOpt("directory").withDescription("directory to save result").hasArg()
                .withArgName("directory").create('d'));

        options.addOption(OptionBuilder.withLongOpt("mode").withDescription("print document by json array/[object].")
                .hasArg().withArgName("mode").create('m'));

        options.addOption(OptionBuilder.withLongOpt("charset").withDescription("charset to read/write file.").hasArg()
                .withArgName("charset").create('c'));

        options.addOption(OptionBuilder.withLongOpt("readCharset").withDescription("charset to read date file[GBK].")
                .hasArg().withArgName("intputCharset").create());

        options.addOption(OptionBuilder.withLongOpt("writeCharset").withDescription("charset to write json file[GBK].")
                .hasArg().withArgName("outputCharset").create());
    }

    public void parseArgs(CommandLine cmd) {
        if (cmd.hasOption("directory")) {
            directory = cmd.getOptionValue("directory");
        }

        if (cmd.hasOption("file")) {
            filename = cmd.getOptionValue("file");
        }

        if (cmd.hasOption("output")) {
            outputFilename = cmd.getOptionValue("output");
        }

        if (cmd.hasOption("list")) {
            listFile = true;
        }

        if (cmd.hasOption("pretty")) {
            String value = cmd.getOptionValue("pretty");

            value = value.toLowerCase();

            if (value.equals("yes") || value.equalsIgnoreCase("true") || value.equals("1")) {
                pretty = true;
            }
        }

        if (cmd.hasOption("mode")) {
            String value = cmd.getOptionValue("mode");

            if (value.equals(DocContentFormat.array)) {
                mode = DocContentFormat.array;
            } else if (value.equals(DocContentFormat.object)) {
                mode = DocContentFormat.object;
            }
        }

        if (cmd.hasOption("charset")) {
            intputCharset = outputCharset = cmd.getOptionValue("charset");
        }

        if (cmd.hasOption("intputCharset")) {
            intputCharset = cmd.getOptionValue("intputCharset");
        }

        if (cmd.hasOption("outputCharset")) {
            outputCharset = cmd.getOptionValue("outputCharset");
        }
    }

    private String getOutputFileName(String name) {
        if (outputFilename != null && !outputFilename.isEmpty()) {
            return outputFilename;
        }

        if (directory != null && !directory.isEmpty()) {
            File file = new File(name);
            return directory + File.separator + file.getName();
        }

        return null;
    }

    private void openOutputStream(String name) throws FileNotFoundException {
        if (outputStream != null) {
            return;
        }

        String filename = getOutputFileName(name);

        if (filename == null) {
            outputToOneFile = true;
            outputStream = System.out;
        } else {
            outputStream = new FileOutputStream(filename);
        }
    }

    private void closeOutputStream() throws IOException {
        if (outputToOneFile) {
            return;
        }

        outputStream.close();

        outputStream = null;
    }

    static class MyPrettyPrinter extends DefaultPrettyPrinter {
        public MyPrettyPrinter() {
            this._arrayIndenter = new Lf2SpacesIndenter();
        }
    }

    public void dumpJsonDoc(OutputStream os, Record doc) throws JsonGenerationException, IOException {
        OutputStreamWriter writer;

        writer = new OutputStreamWriter(os, outputCharset);

        JsonGenerator generator = objectMapper.getJsonFactory().createJsonGenerator(writer);

        if (pretty) {
            MyPrettyPrinter pp = new MyPrettyPrinter();
            generator.setPrettyPrinter(pp);
        }

        //
        generator.writeStartArray();
        {
            switch (mode) {
            case object: {
                generator.writeStartObject();
                {
                    List<Field> fields = doc.getFields();

                    for (Field field : fields) {
                        String value = field.getValue();

                        if (value != null && !value.isEmpty()) {
                            generator.writeStringField(field.getName(), value);
                        }
                    }
                }
                generator.writeEndObject();
                break;
            }
            case array: {
                generator.writeStartArray();
                {
                    List<Field> fields = doc.getFields();

                    for (Field field : fields) {
                        String value = field.getValue();

                        generator.writeString(value);
                    }
                }
                generator.writeEndArray();
                break;
            }
            }
        }
        generator.writeEndArray();

        //
        generator.flush();
        writer.flush();
    }

    private void dumpJson(String name) throws IOException {
        RecordInputStream stream = new RecordInputStream(name, intputCharset);

        stream.setRecordHandler(new RecordHandler() {

            @Override
            public void process(Record record) {
                try {
                    dumpJsonDoc(outputStream, record);
                } catch (JsonGenerationException e) {
                    logger.error("JsonGenerationException", e);
                } catch (IOException e) {
                    logger.error("IOException", e);
                }
            }
        });

        stream.run();

        stream.close();
    }

    private void convertFile(String name) throws IOException {
        openOutputStream(name);

        dumpJson(name);

        closeOutputStream();
    }

    public void run(CommandLine cmd) throws IOException {
        if (directory != null) {
            File file = new File(directory);

            file.mkdirs();
        }

        if (listFile) {
            ListFile listFile = new ListFile(filename);

            for (String i : listFile.getFileNameList()) {
                convertFile(i);
            }
        } else {
            convertFile(filename);
        }
    }

    public void stop() {
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {

            }
        }
    }

    protected void entry(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                stop();
            }
        }));

        CommandLineParser parser = new PosixParser();

        try {
            CommandLine cmd = null;

            {
                Options options = new Options();

                init(options);

                cmd = parser.parse(options, args);
            }

            parseArgs(cmd);

            run(cmd);

        } catch (ParseException e) {
            logger.error("ParseException", e);
        } catch (ExitExeption e) {

        } catch (IOException e) {
            logger.error("ParseException", e);
        }
    }

    public static void main(String[] args) {
        DataFile2Json app = new DataFile2Json();

        app.entry(args);
    }
}
