package org.example.jobscraperweb;

import jakarta.annotation.PostConstruct;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class JobScraper {

    private static final Logger logger = LoggerFactory.getLogger(JobScraper.class);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000; // 2 seconds
    private static final int CONNECTION_TIMEOUT = 15000; // 15 seconds

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private RestTemplate restTemplate;

    private int jobsScraped = 0;

    @PostConstruct
    public void initialScrape() {
        logger.info("Starting initial job scraping...");
        scrapeAndSaveJobs();
    }
   
    @Scheduled(fixedRate = 14400000) // 4 hours
    public void scheduledScrape() {
        logger.info("Scheduled scraping started at: {}", LocalDateTime.now());
        scrapeAndSaveJobs();
    }

    private void scrapeAndSaveJobs() {
        jobsScraped = 0;
        logger.info("Starting job scraping session at: {}", LocalDateTime.now());

        // Execute each scraper with retry logic
        executeWithRetry(this::scrapeWeWorkRemotely, "We Work Remotely");
        executeWithRetry(this::scrapeRemoteOk, "RemoteOK");  
        executeWithRetry(this::scrapeRemotive, "Remotive");

        logger.info("Scraping session completed! {} new jobs added.", jobsScraped);
        logScrapingStatistics();
    }

    private boolean executeWithRetry(Runnable scraperMethod, String sourceName) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                logger.info("Attempting to scrape {} (attempt {}/{})", sourceName, attempt, MAX_RETRIES);
                scraperMethod.run();
                logger.info("Successfully scraped {}", sourceName);
                return true;
            } catch (Exception e) {
                logger.warn("Attempt {}/{} failed for {}: {}", attempt, MAX_RETRIES, sourceName, e.getMessage());
                
                if (attempt < MAX_RETRIES) {
                    try {
                        long delay = RETRY_DELAY_MS * attempt; // Linear backoff
                        logger.info("Waiting {}ms before retry...", delay);
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("Scraper interrupted while waiting for retry");
                        return false;
                    }
                }
            }
        }
        logger.error("All {} attempts failed for {}", MAX_RETRIES, sourceName);
        return false;
    }

    private void scrapeWeWorkRemotely() {
        try {
            logger.info("Fetching jobs from We Work Remotely RSS");
            Document doc = Jsoup.connect("https://weworkremotely.com/remote-jobs.rss")
                    .timeout(CONNECTION_TIMEOUT)
                    .userAgent("Mozilla/5.0 (compatible; JobScraper/1.0)")
                    .get();
            
            Elements items = doc.select("item");
            if (items.isEmpty()) {
                logger.warn("No job items found in We Work Remotely RSS feed");
                return;
            }

            int wwrJobs = 0;
            for (org.jsoup.nodes.Element item : items) {
                try {
                    String title = cleanText(item.select("title").text());
                    String link = cleanText(item.select("link").text());
                    String company = cleanText(item.select("dc|creator").text());

                    // Handle title parsing for company extraction
                    if (company.isBlank() && title.contains(":")) {
                        String[] parts = title.split(":", 2);
                        if (parts.length == 2) {
                            company = parts[0].trim();
                            title = parts[1].trim();
                        }
                    }

                    if (isValidJobData(title, company, link)) {
                        if (!jobRepository.existsByUrl(link)) {
                            Job job = createJob(title, company, link, "We Work Remotely");
                            jobRepository.save(job);
                            jobsScraped++;
                            wwrJobs++;
                        }
                    } else {
                        logger.debug("Skipping invalid We Work Remotely job data");
                    }
                } catch (Exception e) {
                    logger.warn("Error processing We Work Remotely item: {}", e.getMessage());
                }
            }
            logger.info("We Work Remotely: {} new jobs added", wwrJobs);
            
        } catch (Exception e) {
            logger.error("Error fetching from We Work Remotely: {}", e.getMessage());
            throw new RuntimeException("We Work Remotely scraping failed", e);
        }
    }

    private void scrapeRemoteOk() {
        try {
            String url = "https://remoteok.com/api";
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            logger.info("Fetching jobs from RemoteOK API");
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("RemoteOK API returned status: " + response.getStatusCode());
            }

            String jsonResponse = response.getBody();
            if (jsonResponse == null || jsonResponse.isBlank()) {
                logger.warn("RemoteOK API returned empty response");
                return;
            }

            JSONArray jsonArray = new JSONArray(jsonResponse.trim());
            if (jsonArray.length() <= 1) {
                logger.warn("RemoteOK API returned no job data");
                return;
            }
            
            int remoteOkJobs = 0;
            // Skip first element (metadata)
            for (int i = 1; i < jsonArray.length(); i++) {
                try {
                    JSONObject jobObject = jsonArray.getJSONObject(i);

                    String title = cleanText(jobObject.optString("position", ""));
                    String company = cleanText(jobObject.optString("company", ""));
                    String link = cleanText(jobObject.optString("url", ""));

                    // Ensure URL is absolute
                    if (!link.startsWith("http") && !link.isBlank()) {
                        link = "https://remoteok.com" + link;
                    }

                    if (isValidJobData(title, company, link)) {
                        if (!jobRepository.existsByUrl(link)) {
                            Job job = createJob(title, company, link, "RemoteOK");
                            jobRepository.save(job);
                            jobsScraped++;
                            remoteOkJobs++;
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error processing RemoteOK job {}: {}", i, e.getMessage());
                }
            }
            logger.info("RemoteOK: {} new jobs added", remoteOkJobs);
            
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                logger.warn("Rate limited by RemoteOK API");
                throw new RuntimeException("RemoteOK rate limit exceeded", e);
            } else {
                logger.error("HTTP client error from RemoteOK: {} - {}", e.getStatusCode(), e.getMessage());
                throw new RuntimeException("RemoteOK API client error", e);
            }
        } catch (HttpServerErrorException e) {
            logger.error("HTTP server error from RemoteOK: {} - {}", e.getStatusCode(), e.getMessage());
            throw new RuntimeException("RemoteOK API server error", e);
        } catch (ResourceAccessException e) {
            logger.error("Network error accessing RemoteOK: {}", e.getMessage());
            throw new RuntimeException("RemoteOK network error", e);
        } catch (Exception e) {
            logger.error("Unexpected error fetching from RemoteOK: {}", e.getMessage());
            throw new RuntimeException("RemoteOK scraping failed", e);
        }
    }

    private void scrapeRemotive() {
        try {
            String url = "https://remotive.com/api/remote-jobs";
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            logger.info("Fetching jobs from Remotive API");
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Remotive API returned status: " + response.getStatusCode());
            }

            String jsonResponse = response.getBody();
            if (jsonResponse == null || jsonResponse.isBlank()) {
                logger.warn("Remotive API returned empty response");
                return;
            }

            JSONObject jsonObject = new JSONObject(jsonResponse);
            
            if (!jsonObject.has("jobs")) {
                logger.warn("Remotive API response missing 'jobs' key");
                return;
            }
            
            JSONArray jobsArray = jsonObject.getJSONArray("jobs");
            logger.info("Remotive API returned {} jobs", jobsArray.length());

            int remotiveJobs = 0;
            for (int i = 0; i < jobsArray.length(); i++) {
                try {
                    JSONObject jobObject = jobsArray.getJSONObject(i);

                    String title = cleanText(jobObject.optString("title", ""));
                    String company = cleanText(jobObject.optString("company_name", ""));
                    String link = cleanText(jobObject.optString("url", ""));

                    // Ensure URL is absolute
                    if (!link.startsWith("http") && !link.isBlank()) {
                        link = "https://remotive.com" + link;
                    }

                    if (isValidJobData(title, company, link)) {
                        if (!jobRepository.existsByUrl(link)) {
                            Job job = createJob(title, company, link, "Remotive");
                            jobRepository.save(job);
                            jobsScraped++;
                            remotiveJobs++;
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error processing Remotive job {}: {}", i, e.getMessage());
                }
            }
            logger.info("Remotive: {} new jobs added", remotiveJobs);
            
        } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
            logger.error("HTTP error fetching from Remotive: {}", e.getMessage());
            throw new RuntimeException("Remotive API error", e);
        } catch (Exception e) {
            logger.error("Unexpected error fetching from Remotive: {}", e.getMessage());
            throw new RuntimeException("Remotive scraping failed", e);
        }
    }

    // Helper methods
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (compatible; JobScraper/1.0)");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private String cleanText(String text) {
        return text != null ? text.trim() : "";
    }

    private boolean isValidJobData(String title, String company, String link) {
        return !title.isBlank() && !company.isBlank() && !link.isBlank() && 
               link.startsWith("http");
    }

    private Job createJob(String title, String company, String url, String source) {
        Job job = new Job();
        job.setTitle(title);
        job.setCompany(company);
        job.setUrl(url);
        job.setSource(source);
        job.setType("Remote");
        return job;
    }

    private void logScrapingStatistics() {
        try {
            long wwrCount = jobRepository.countBySource("We Work Remotely");
            long remoteOkCount = jobRepository.countBySource("RemoteOK");
            long remotiveCount = jobRepository.countBySource("Remotive");
            long totalCount = jobRepository.count();
            
            logger.info("Database Statistics:");
            logger.info("- We Work Remotely: {} jobs", wwrCount);
            logger.info("- RemoteOK: {} jobs", remoteOkCount);
            logger.info("- Remotive: {} jobs", remotiveCount);
            logger.info("- Total: {} jobs", totalCount);
        } catch (Exception e) {
            logger.warn("Could not generate statistics: {}", e.getMessage());
        }
    }
}