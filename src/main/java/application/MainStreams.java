package application;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import spark.Request;
import spark.Response;
import spark.utils.IOUtils;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static spark.Spark.*;

public class MainStreams {

    private static final String SERVICE_URL = "http://wsf.cdyne.com/WeatherWS/Weather.asmx ";
    private static AtomicLong count = new AtomicLong();
    private static final String SECRET_KEY = "94304";

    public static void main(String[] args) {
        port(8080);
        get("/", ((req, res) -> "Try sending xml through post request containing secret key. " +
                "Successful count: " + count));
        post("/", MainStreams::handlePost);
    }

    private static Object handlePost(Request req, Response res) throws ParserConfigurationException, IOException, SAXException {
        if (hasPass(req)) {
            count.incrementAndGet();

            HttpURLConnection conn = (HttpURLConnection) new URL(SERVICE_URL).openConnection();
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "text/xml");
            conn.setRequestProperty("charset", "utf-8");
            conn.setRequestProperty("Content-Length", Integer.toString(req.bodyAsBytes().length));
            conn.getOutputStream().write(req.bodyAsBytes());

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                return IOUtils.toString(conn.getInputStream());
            }
            return "You had passkey, but server couldn't send request to url";
        }
        res.redirect("/");
        return "";
    }

    private static boolean hasPass(Request req) throws ParserConfigurationException, IOException, SAXException {
        byte[] bytes = req.bodyAsBytes();
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        doc.getDocumentElement().normalize();
        NodeList elements = doc.getElementsByTagName("*");
        return IntStream.range(0, elements.getLength())
                .parallel()
                .mapToObj(elements::item)
                .map(MainStreams::getEverything)
                .flatMap(Function.identity())
                .anyMatch(MainStreams::find);
    }

    private static boolean find(String s) {
        return Pattern.compile(Pattern.quote(SECRET_KEY), Pattern.CASE_INSENSITIVE).matcher(s).find();
    }

    private static Stream<String> getEverything(Node n) {
        Stream.Builder<String> all = Stream.builder();
        if (n.getNodeName() != null) all.accept(n.getNodeName());
        if (n.getNodeValue() != null) all.accept(n.getNodeValue());
        if (n.getTextContent() != null) all.add(n.getTextContent());
        if (n.hasAttributes()) {
            NamedNodeMap attributes = n.getAttributes();
            IntStream.range(0, attributes.getLength())
                    .mapToObj(attributes::item)
                    .map(MainStreams::getEverythingFromAttribute)
                    .flatMap(Function.identity())
                    .forEach(all::accept);
        }
        return all.build();
    }

    private static Stream<String> getEverythingFromAttribute(Node s) {
        Stream.Builder<String> attrs = Stream.builder();
        if (s.getNodeName() != null) attrs.accept(s.getNodeName());
        if (s.getNodeValue() != null) attrs.accept(s.getNodeValue());
        if (s.getTextContent() != null) attrs.add(s.getTextContent());
        return attrs.build();
    }

}
