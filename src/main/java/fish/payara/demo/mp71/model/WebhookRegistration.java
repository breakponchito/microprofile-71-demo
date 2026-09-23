package fish.payara.demo.mp71.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "WebhookRegistration", description = "Registers a callback URL to receive book events")
public class WebhookRegistration {

    @Schema(description = "URL the server will POST BookEvent payloads to",
            example = "https://my-service.example.com/hooks/books",
            required = true)
    private String callbackUrl;

    public WebhookRegistration() {}

    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
}