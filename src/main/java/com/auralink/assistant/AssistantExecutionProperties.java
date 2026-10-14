package com.auralink.assistant;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Execution is a second explicit opt-in, independent of foundation storage. */
@ConfigurationProperties("auralink.assistant.execution")
public class AssistantExecutionProperties {
 private boolean enabled;
 private String aiBaseUrl="", internalTokenFile="", expectedConfigSha256="";
 private boolean allowLoopbackHttp;
 private int concurrency=2, ownerConcurrency=2, maxEvents=512, maxContextMessages=32, maxContextChars=32000;
 private int maxOutputChars=64000, maxSubscriptions=8, maxSummaryPerHour=3, summaryTurnThreshold=4;
 private boolean automaticSummaries;
 private int globalPending=64,ownerPending=16;
 public int getGlobalPending(){return globalPending;} public void setGlobalPending(int v){globalPending=v;}
 public int getOwnerPending(){return ownerPending;} public void setOwnerPending(int v){ownerPending=v;}
 private Duration retrievalTimeout=Duration.ofSeconds(31);
 public Duration getRetrievalTimeout(){return retrievalTimeout;} public void setRetrievalTimeout(Duration v){retrievalTimeout=v;}
 private Duration pollInterval=Duration.ofMillis(250), lease=Duration.ofSeconds(15), totalTimeout=Duration.ofSeconds(90), idleTimeout=Duration.ofSeconds(20), subscriptionTimeout=Duration.ofSeconds(25), summaryIdle=Duration.ofSeconds(30);
 public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
 public String getAiBaseUrl(){return aiBaseUrl;} public void setAiBaseUrl(String v){aiBaseUrl=v;}
 public String getInternalTokenFile(){return internalTokenFile;} public void setInternalTokenFile(String v){internalTokenFile=v;}
 public String getExpectedConfigSha256(){return expectedConfigSha256;} public void setExpectedConfigSha256(String v){expectedConfigSha256=v;}
 public boolean isAllowLoopbackHttp(){return allowLoopbackHttp;} public void setAllowLoopbackHttp(boolean v){allowLoopbackHttp=v;}
 public int getConcurrency(){return concurrency;} public void setConcurrency(int v){concurrency=v;}
 public int getOwnerConcurrency(){return ownerConcurrency;} public void setOwnerConcurrency(int v){ownerConcurrency=v;}
 public int getMaxEvents(){return maxEvents;} public void setMaxEvents(int v){maxEvents=v;}
 public int getMaxContextMessages(){return maxContextMessages;} public void setMaxContextMessages(int v){maxContextMessages=v;}
 public int getMaxContextChars(){return maxContextChars;} public void setMaxContextChars(int v){maxContextChars=v;}
 public int getMaxOutputChars(){return maxOutputChars;} public void setMaxOutputChars(int v){maxOutputChars=v;}
 public int getMaxSubscriptions(){return maxSubscriptions;} public void setMaxSubscriptions(int v){maxSubscriptions=v;}
 public int getMaxSummaryPerHour(){return maxSummaryPerHour;} public void setMaxSummaryPerHour(int v){maxSummaryPerHour=v;}
 public int getSummaryTurnThreshold(){return summaryTurnThreshold;} public void setSummaryTurnThreshold(int v){summaryTurnThreshold=v;}
 public boolean isAutomaticSummaries(){return automaticSummaries;} public void setAutomaticSummaries(boolean v){automaticSummaries=v;}
 public Duration getPollInterval(){return pollInterval;} public void setPollInterval(Duration v){pollInterval=v;}
 public Duration getLease(){return lease;} public void setLease(Duration v){lease=v;}
 public Duration getTotalTimeout(){return totalTimeout;} public void setTotalTimeout(Duration v){totalTimeout=v;}
 public Duration getIdleTimeout(){return idleTimeout;} public void setIdleTimeout(Duration v){idleTimeout=v;}
 public Duration getSubscriptionTimeout(){return subscriptionTimeout;} public void setSubscriptionTimeout(Duration v){subscriptionTimeout=v;}
 public Duration getSummaryIdle(){return summaryIdle;} public void setSummaryIdle(Duration v){summaryIdle=v;}
 public void validate(){
  if(!enabled) return;
  if(expectedConfigSha256==null||!expectedConfigSha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Assistant execution requires an AI configuration digest");
  if(globalPending<1||globalPending>1024||ownerPending<1||ownerPending>globalPending||concurrency<1||concurrency>4||ownerConcurrency<1||ownerConcurrency>concurrency||maxEvents<16||maxEvents>4096||maxContextMessages<2||maxContextMessages>100||maxContextChars<1000||maxContextChars>64000||maxOutputChars<100||maxOutputChars>128000||maxSubscriptions<1||maxSubscriptions>32||maxSummaryPerHour<1||maxSummaryPerHour>12||summaryTurnThreshold<1||summaryTurnThreshold>50) throw new IllegalArgumentException("Assistant execution limits are invalid");
  duration(retrievalTimeout,100,32000);duration(pollInterval,50,5000);duration(lease,1000,120000);duration(totalTimeout,500,300000);duration(idleTimeout,200,60000);duration(subscriptionTimeout,500,60000);duration(summaryIdle,0,3600000);
 }
 private static void duration(Duration v,long min,long max){if(v==null||v.toMillis()<min||v.toMillis()>max)throw new IllegalArgumentException("Assistant execution duration is invalid");}
}
