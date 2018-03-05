package awsstats.ec2stats;

import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.ini4j.Ini;
import org.ini4j.Profile;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ArgsParser {
    String accessKey;
    String secretAccess;
    String url;
    Boolean noanalysis;
    String loadStats;
    Boolean quiet;
    Threshold threshold;
    String configPath;
    String profile;

    public ArgsParser() {

    }

    public Boolean parseArgs(String[] args) {
        CommandLine cmd;
        Options options = new Options();
        CommandLineParser parser = new DefaultParser();

        Option option_h = Option.builder("h").desc("Help").longOpt("help").build();

        Option option_k = Option.builder("k").desc("access key")
                .longOpt("access_key").hasArg().build();
        Option option_s = Option.builder("s").desc("secret access key")
                .longOpt("secret_key").hasArg().build();
        Option option_u = Option.builder("u").desc("server url")
                .longOpt("url").hasArg().build();
        Option option_a = Option.builder().desc("send stats to server to analyze")
                .longOpt("noanalysis").build();
        Option option_l = Option.builder("l").desc("stats file name to load data from")
                .longOpt("load_stats").hasArg().build();
        Option option_q = Option.builder().desc("print summary details")
                .longOpt("quiet").build();
        Option option_t = Option.builder("t").desc("[average max] CPU threshold")
                .longOpt("threshold").hasArgs().build();
        Option option_c = Option.builder("c").desc("Parent path to config file")
                .longOpt("config_path").hasArg().build();
        Option option_p = Option.builder("p").desc("AWS credential profile")
                .longOpt("profile").hasArg().build();

        options.addOption(option_h);
        options.addOption(option_k);
        options.addOption(option_s);
        options.addOption(option_u);
        options.addOption(option_a);
        options.addOption(option_l);
        options.addOption(option_q);
        options.addOption(option_t);
        options.addOption(option_c);
        options.addOption(option_p);
        HelpFormatter formatter = new HelpFormatter();

        try {
            cmd = parser.parse(options, args);

            if (cmd.hasOption("h")) {
                formatter.printHelp( "ec2stats", options );
                return false;
            }

            if (cmd.hasOption("k")) {
                this.accessKey = cmd.getOptionValue("k");
            } else {
                this.accessKey = "";
            }

            if (cmd.hasOption("s")) {
                this.secretAccess = cmd.getOptionValue("s");
            } else {
                this.secretAccess = "";
            }

            if (cmd.hasOption("u")) {
                this.url = cmd.getOptionValue("u");
            } else {
                this.url = "https://customer.fittedcloud.com/v1/ec2stats";
            }

            if (cmd.hasOption("noanalysis")) {
                this.noanalysis = true;
            } else {
                this.noanalysis = false;
            }

            if (cmd.hasOption("l")) {
                this.loadStats = cmd.getOptionValue("l");
            } else {
                this.loadStats = "";
            }

            if (cmd.hasOption("quiet")) {
                this.quiet = true;
            } else {
                this.quiet = false;
            }

            if (cmd.hasOption("t")) {
                this.threshold = new Threshold();
                this.threshold.setThreshold(Integer.valueOf(cmd.getOptionValues("t")[0]),
                        Integer.valueOf(cmd.getOptionValues("t")[1]));
            } else {
                this.threshold = new Threshold();
                this.threshold.setThreshold(5, 30);
            }

            if (cmd.hasOption("c")) {
                this.configPath = cmd.getOptionValue("c");
            } else {
                this.configPath = "";
            }

            if (cmd.hasOption("p")) {
                this.profile = cmd.getOptionValue("p");
            } else {
                this.profile = "default";
            }
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp( "ec2stats", options );
            return false;
        } catch (Exception e) {
            formatter.printHelp( "ec2stats", options );
            return false;
        }

        return true;
    }

    private void getCredential() {
        if (this.accessKey.length() > 0 && this.secretAccess.length() > 0)
            return;

        Path configFile;
        if (this.configPath.length() > 0) {
            configFile = Paths.get(this.configPath, "credentials");
        } else {
            configFile = Paths.get(Paths.get("").toAbsolutePath().toString(), ".botoconfig", "credentials");
        }

        try {
            Ini ini = new Ini(new FileInputStream(configFile.toString()));
            Profile.Section section = ini.get(this.profile);  // "dev", "prod", etc.

            this.accessKey = section.get("aws_access_key_id");
            this.secretAccess = section.get("aws_secret_access_key");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public String getAccessKey() {
        if (this.accessKey.length() == 0)
            getCredential();

        return this.accessKey;
    }

    public String getSecretAccess() {
        if (this.accessKey.length() == 0)
            getCredential();
        return this.secretAccess;
    }

    public String getUrl() {
        return this.url;
    }

    public Boolean getNoAnalysis() {
        return this.noanalysis;
    }

    public String getLoadStats() {
        return this.loadStats;
    }

    public Boolean getQuiet() {
        return this.quiet;
    }

    public Threshold getThreshold() {
        return this.threshold;
    }

    public String getConfigPath() {
        return this.configPath;
    }

    public String getProfile() {
        return this.profile;
    }
}
