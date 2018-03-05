package awsstats.ec2stats;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;

public class MyCredential implements AWSCredentialsProvider {
    public MyCredential(String accessKey, String secretAccess) {
        super();
        this.accessKey = accessKey;
        this.secretAccess = secretAccess;
    }

    private String accessKey;
    private String secretAccess;

    public AWSCredentials getCredentials() {
        AWSCredentials awsCredentials = new AWSCredentials() {

            public String getAWSSecretKey() {
                return secretAccess;
            }

            public String getAWSAccessKeyId() {
                return accessKey;
            };
        };
        return awsCredentials;
    }

    public void refresh() {
    }

}
