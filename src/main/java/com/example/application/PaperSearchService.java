package com.example.application;

import com.example.domain.Paper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

class PaperSearchService {

    // --- Private response records for API deserialization ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record S2SearchResponse(List<S2Paper> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record S2Paper(
            String paperId,
            Map<String, String> externalIds,
            String title,
            @JsonProperty("abstract") String abstractText,
            List<S2Author> authors,
            Integer year,
            String publicationDate,
            String url
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record S2Author(String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PubMedSearchResponse(ESearchResult esearchresult) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ESearchResult(List<String> idlist) {}

    // --- Fields ---

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String s2ApiKey;
    private final String ncbiApiKey;
    private final String ncbiTool;
    private final String ncbiEmail;
    private final int maxResults;

    PaperSearchService(Config config) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        this.s2ApiKey    = config.hasPath("paper-search.s2-api-key")   ? config.getString("paper-search.s2-api-key")   : null;
        this.ncbiApiKey  = config.hasPath("paper-search.ncbi-api-key") ? config.getString("paper-search.ncbi-api-key") : null;
        this.ncbiTool    = config.hasPath("paper-search.ncbi-tool")    ? config.getString("paper-search.ncbi-tool")    : "research-podcast-creator";
        this.ncbiEmail   = config.hasPath("paper-search.ncbi-email")   ? config.getString("paper-search.ncbi-email")   : "contact@example.com";
        this.maxResults  = config.hasPath("paper-search.max-results")  ? config.getInt("paper-search.max-results")     : 10;
    }

    List<Paper> searchPapers(String query) {
        Map<String, Paper> deduplicated = new LinkedHashMap<>();
        searchSemanticScholar(query, deduplicated);
        searchArXiv(query, deduplicated);
        searchPubMed(query, deduplicated);
        return deduplicated.values().stream().limit(maxResults).toList();
    }

    // --- Semantic Scholar ---

    private void searchSemanticScholar(String query, Map<String, Paper> deduplicated) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://api.semanticscholar.org/graph/v1/paper/search?query=" + encoded
                    + "&limit=10&fields=externalIds,title,abstract,authors,year,publicationDate,url";

            var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET();
            if (s2ApiKey != null) requestBuilder.header("x-api-key", s2ApiKey);

            var resp = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                var parsed = mapper.readValue(resp.body(), S2SearchResponse.class);
                if (parsed.data() != null) {
                    for (var p : parsed.data()) {
                        if (p.title() == null || p.title().isBlank()) continue;
                        String key = canonicalKey(p.externalIds(), "s2:" + p.paperId());
                        deduplicated.putIfAbsent(key, toS2Paper(p, key));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Semantic Scholar search failed: " + e.getMessage());
        }
    }

    private Paper toS2Paper(S2Paper p, String key) {
        List<String> authors = p.authors() != null
                ? p.authors().stream().map(S2Author::name).filter(Objects::nonNull).toList()
                : List.of();
        String date = p.publicationDate() != null ? p.publicationDate()
                : (p.year() != null ? p.year().toString() : null);
        String url = p.url() != null ? p.url()
                : "https://www.semanticscholar.org/paper/" + p.paperId();
        return new Paper(key, p.title(), authors, date, Paper.Source.SEMANTIC_SCHOLAR, url, p.abstractText());
    }

    // --- arXiv ---

    private void searchArXiv(String query, Map<String, Paper> deduplicated) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "http://export.arxiv.org/api/query?search_query=all:" + encoded
                    + "&max_results=10&sortBy=relevance";

            var resp = httpClient.send(
                    HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                parseArXivAtom(resp.body(), deduplicated);
            }
        } catch (Exception e) {
            System.err.println("Warning: arXiv search failed: " + e.getMessage());
        }
    }

    private void parseArXivAtom(String xml, Map<String, Paper> deduplicated) throws Exception {
        var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
        var entries = doc.getElementsByTagName("entry");
        for (int i = 0; i < entries.getLength(); i++) {
            var entry = (Element) entries.item(i);
            String rawId = firstText(entry, "id");
            if (rawId.isBlank()) continue;
            String arxivId = rawId.replaceAll(".*abs/", "").replaceAll("v\\d+$", "").trim();

            String title = firstText(entry, "title").replaceAll("\\s+", " ").trim();
            if (title.isBlank()) continue;
            String abstractText = firstText(entry, "summary").replaceAll("\\s+", " ").trim();
            String published = firstText(entry, "published");
            if (published.length() > 10) published = published.substring(0, 10);

            List<String> authors = new ArrayList<>();
            NodeList authorNodes = entry.getElementsByTagName("author");
            for (int j = 0; j < authorNodes.getLength(); j++) {
                var author = (Element) authorNodes.item(j);
                NodeList nameNodes = author.getElementsByTagName("name");
                if (nameNodes.getLength() > 0)
                    authors.add(nameNodes.item(0).getTextContent().trim());
            }

            Map<String, String> ids = new HashMap<>();
            ids.put("ArXiv", arxivId);
            // Check for DOI in arxiv namespace
            NodeList doiNodes = entry.getElementsByTagNameNS("http://arxiv.org/schemas/atom", "doi");
            if (doiNodes.getLength() > 0) ids.put("DOI", doiNodes.item(0).getTextContent().trim());

            String key = canonicalKey(ids, "arxiv:" + arxivId);
            if (!deduplicated.containsKey(key)) {
                deduplicated.put(key, new Paper(key, title, authors, published.isBlank() ? null : published,
                        Paper.Source.ARXIV, "https://arxiv.org/abs/" + arxivId,
                        abstractText.isBlank() ? null : abstractText));
            }
        }
    }

    // --- PubMed ---

    private void searchPubMed(String query, Map<String, Paper> deduplicated) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String common = "&tool=" + URLEncoder.encode(ncbiTool, StandardCharsets.UTF_8)
                    + "&email=" + URLEncoder.encode(ncbiEmail, StandardCharsets.UTF_8)
                    + (ncbiApiKey != null ? "&api_key=" + ncbiApiKey : "");

            String esearchUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi"
                    + "?db=pubmed&term=" + encoded + "&retmax=10&retmode=json" + common;

            var esResp = httpClient.send(
                    HttpRequest.newBuilder().uri(URI.create(esearchUrl)).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (esResp.statusCode() != 200) return;

            var esResult = mapper.readValue(esResp.body(), PubMedSearchResponse.class);
            List<String> pmids = esResult.esearchresult() != null ? esResult.esearchresult().idlist() : null;
            if (pmids == null || pmids.isEmpty()) return;

            String efetchUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi"
                    + "?db=pubmed&id=" + String.join(",", pmids) + "&retmode=xml" + common;

            var efResp = httpClient.send(
                    HttpRequest.newBuilder().uri(URI.create(efetchUrl)).timeout(Duration.ofSeconds(15)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (efResp.statusCode() == 200) {
                parsePubMedXml(efResp.body(), deduplicated);
            }
        } catch (Exception e) {
            System.err.println("Warning: PubMed search failed: " + e.getMessage());
        }
    }

    private void parsePubMedXml(String xml, Map<String, Paper> deduplicated) throws Exception {
        var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
        var articles = doc.getElementsByTagName("PubmedArticle");
        for (int i = 0; i < articles.getLength(); i++) {
            var article = (Element) articles.item(i);
            String pmid    = firstText(article, "PMID");
            String title   = firstText(article, "ArticleTitle");
            if (title.isBlank()) continue;
            String abstractText = firstText(article, "AbstractText");

            String doi = null;
            NodeList articleIds = article.getElementsByTagName("ArticleId");
            for (int j = 0; j < articleIds.getLength(); j++) {
                var aid = (Element) articleIds.item(j);
                if ("doi".equals(aid.getAttribute("IdType"))) doi = aid.getTextContent().trim();
            }

            List<String> authors = new ArrayList<>();
            NodeList authorNodes = article.getElementsByTagName("Author");
            for (int j = 0; j < authorNodes.getLength(); j++) {
                var author = (Element) authorNodes.item(j);
                String last  = firstText(author, "LastName");
                String first = firstText(author, "ForeName");
                if (!last.isBlank()) authors.add(first.isBlank() ? last : first + " " + last);
            }

            String pubYear  = firstText(article, "Year");
            String pubMonth = firstText(article, "Month");
            String pubDate  = pubYear.isBlank() ? null : pubYear + (pubMonth.isBlank() ? "" : "-" + pubMonth);

            Map<String, String> ids = new HashMap<>();
            if (!pmid.isBlank()) ids.put("PubMed", pmid);
            if (doi != null)     ids.put("DOI", doi);

            String key = canonicalKey(ids, "pmid:" + pmid);
            if (!deduplicated.containsKey(key)) {
                deduplicated.put(key, new Paper(key, title, authors, pubDate,
                        Paper.Source.PUBMED, "https://pubmed.ncbi.nlm.nih.gov/" + pmid + "/",
                        abstractText.isBlank() ? null : abstractText));
            }
        }
    }

    // --- Utilities ---

    private String canonicalKey(Map<String, String> ids, String fallback) {
        if (ids != null) {
            if (ids.containsKey("DOI"))    return "doi:" + ids.get("DOI").toLowerCase().trim();
            if (ids.containsKey("PubMed")) return "pmid:" + ids.get("PubMed").trim();
            if (ids.containsKey("ArXiv"))  return "arxiv:" + ids.get("ArXiv").replaceAll("v\\d+$", "").trim();
        }
        return fallback;
    }

    private String firstText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent().trim() : "";
    }
}
