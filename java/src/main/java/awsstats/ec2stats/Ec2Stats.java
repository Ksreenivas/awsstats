package awsstats.ec2stats;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class Ec2Stats {
    private final List<String> regionsList = Arrays.asList("us-west-2"/*, "us-east-2", "us-west-1", "us-west-2"*/);
    private MyCredential credential;
    Integer days = 14;
    Integer period = 900;

    public Ec2Stats() {
        this.credential = null;
    }

    public void setCredential(String accessKey, String secretAccess) {
        this.credential = new MyCredential(accessKey, secretAccess);
    }

    private List<Datapoint> collectCpuStats(AmazonCloudWatch cw, String instanceId) {
        List<Datapoint> datapoints = new ArrayList<Datapoint>();
        try {
            long offsetInMilliseconds = 1000 * 60 * 60 * 24 * this.days;
            GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                    .withStartTime(new Date(new Date().getTime() - offsetInMilliseconds))
                    .withNamespace("AWS/EC2")
                    .withPeriod(period)
                    .withDimensions(new Dimension().withName("InstanceId").withValue(instanceId))
                    .withMetricName("CPUUtilization")
                    .withStatistics("Average", "Maximum")
                    .withEndTime(new Date());
            GetMetricStatisticsResult getMetricStatisticsResult = cw.getMetricStatistics(request);

            datapoints.addAll(getMetricStatisticsResult.getDatapoints());
            return datapoints;
        } catch (AmazonServiceException e) {
            System.out.print(e.getMessage());
        }
        return datapoints;

    }

    private StatsData collectCpuStatsAll() {
        List <InstanceData> instances = new ArrayList<InstanceData>();
        String ownerId = "";

        for(String region : regionsList) {
            System.out.printf("Collecting stats in %s ...\n", region);
            final AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard()
                                                        .withRegion(region)
                                                        .withCredentials(this.credential)
                                                        .build();
            final AmazonCloudWatch cw = AmazonCloudWatchClientBuilder.standard()
                                                        .withRegion(region)
                                                        .withCredentials(this.credential)
                                                        .build();;
            boolean done = false;

            DescribeInstancesRequest request = new DescribeInstancesRequest();
            while (!done) {
                DescribeInstancesResult response = ec2.describeInstances(request);

                for (Reservation reservation : response.getReservations()) {
                    if(ownerId.length() == 0)
                        ownerId = String.format("%016x", reservation.getOwnerId().hashCode());

                    for (Instance instance : reservation.getInstances()) {
                        InstanceData instanceData = new InstanceData();
                        instanceData.setRegion(region);
                        instanceData.setInstanceId(instance.getInstanceId());
                        instanceData.setInstanceType(instance.getInstanceType());
                        instanceData.setState(instance.getState().getName());
                        instanceData.setTags(instance.getTags());
                        instanceData.setStats(collectCpuStats(cw, instance.getInstanceId()));
                        instances.add(instanceData);
                    }
                }

                request.setNextToken(response.getNextToken());

                if (response.getNextToken() == null) {
                    done = true;
                }
            }
        }

        StatsData statsData = new StatsData();
        statsData.setOwnerId(ownerId);
        statsData.setInstances(instances);

        return statsData;
    }

    public String serializeStatsData(StatsData statsData) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.UPPER_CAMEL_CASE);
        objectMapper.setSerializationInclusion(Include.NON_NULL);
        String json = "";
        try {
            json = objectMapper.writeValueAsString(statsData);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return json;
    }

    public StatsData deserializeStatsData(String json) {
        StatsData statsData = null;
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.UPPER_CAMEL_CASE);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try {
            statsData = objectMapper.readValue(json, StatsData.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return statsData;
    }

    public Summary deserializeSummaryData(String json) {
        SummaryData summary = null;
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.UPPER_CAMEL_CASE);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try {
            summary = objectMapper.readValue(json, SummaryData.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return summary.summary;
    }

    public void saveObject(String json, String prefix) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String fileName = prefix + "-" + now.format(formatter) + ".json";
        try {
            Files.write(Paths.get(fileName), json.getBytes());
        } catch (IOException e) {
            e.printStackTrace();;
        }
    }

    public void printSummary() {

    }

    public void analyzeStats(String jsonStats, String url, Boolean quiet) {
        System.out.println("Analyzing stats ...");
        HttpClient httpClient = HttpClientBuilder.create().build();
        try {
            HttpPost request = new HttpPost(url);
            request.addHeader("content-type", "application/json");
            StringEntity jsonEntity = new StringEntity(jsonStats);
            request.setEntity(jsonEntity);
            HttpResponse response = httpClient.execute(request);
            if (response.getStatusLine().getStatusCode() != 200) {
                System.out.println(response.toString());
                return;
            }
            HttpEntity entity = response.getEntity();
            String responseJSON = EntityUtils.toString(entity,"UTF-8");
            saveObject(responseJSON, "ec2summary");
            Summary summary = deserializeSummaryData(responseJSON);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public StatsData loadStatsFile(String name) {
        String json;
        try {
            json = new String(Files.readAllBytes(Paths.get(name)));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return deserializeStatsData(json);
    }

    public static void main(String[] args)
    {
        ArgsParser argsParser = new ArgsParser();
        if (!argsParser.parseArgs(args)) return;

        Ec2Stats ec2Stats = new Ec2Stats();
        StatsData statsData = null;
        if (argsParser.getLoadStats().length() > 0) {
            statsData = ec2Stats.loadStatsFile(argsParser.getLoadStats());
        } else {
            ec2Stats.setCredential(argsParser.getAccessKey(), argsParser.getSecretAccess());
            statsData = ec2Stats.collectCpuStatsAll();
        }

        statsData.setThreshold(argsParser.getThreshold().avg, argsParser.getThreshold().max);
        String jsonStats = ec2Stats.serializeStatsData(statsData);
        ec2Stats.saveObject(jsonStats, "ec2stats");

        if (argsParser.getNoAnalysis())
            return;

        ec2Stats.analyzeStats(jsonStats, argsParser.getUrl(), argsParser.getQuiet());

    }
}
