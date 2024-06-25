package emissary.test.core.junit5;

import emissary.core.BaseDataObject;
import emissary.core.IBaseDataObject;
import emissary.core.IBaseDataObjectHelper;
import emissary.core.IBaseDataObjectXmlCodecs;
import emissary.core.IBaseDataObjectXmlCodecs.ElementDecoders;
import emissary.core.IBaseDataObjectXmlCodecs.ElementEncoders;
import emissary.place.IServiceProviderPlace;
import emissary.test.core.junit5.LogbackTester.SimplifiedLogEvent;
import emissary.util.ByteUtil;

import com.google.errorprone.annotations.ForOverride;
import org.apache.commons.lang3.ArrayUtils;
import org.jdom2.Document;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * <p>
 * This test acts similarly to ExtractionTest; however, it compares the entire BDO instead of just what is defined in
 * the XML. In other words, the XML must define exactly the output of the Place, no more and no less. There are methods
 * provided to generate the XML required.
 * </p>
 * 
 * <p>
 * To implement this for a test, you should:
 * </p>
 * <ol>
 * <li>Extend this class</li>
 * <li>Either Override {@link #generateAnswers()} to return true or set the generateAnswers system property to true,
 * which will generate the XML answer files</li>
 * <li>Optionally, to generate answers file without changing code run {@code mvn clean test -DgenerateAnswers=true}</li>
 * <li>Optionally, override the various provided methods if you want to customise the behaviour of providing the IBDO
 * before/after processing</li>
 * <li>Run the tests, which should pass - if they don't, you either have incorrect processing which needs fixing, or you
 * need to further customise the initial/final IBDOs.</li>
 * <li>Once the tests pass, you can remove the overridden method(s) added above.</li>
 * </ol>
 */
public abstract class RegressionTest extends ExtractionTest {
    /**
     * The list of actual logEvents generated by executing the place.
     */
    protected List<SimplifiedLogEvent> actualSimplifiedLogEvents;

    /**
     * Override this or set the generateAnswers system property to true to generate XML for data files.
     * 
     * @return defaults to false if no XML should be generated (i.e. normal case of executing tests) or true to generate
     *         automatically
     */
    @ForOverride
    protected boolean generateAnswers() {
        return Boolean.getBoolean("generateAnswers");
    }

    /**
     * Allow the initial IBDO to be overridden - for example, adding additional previous forms
     * 
     * This is used in the simple case to generate an IBDO from the file on disk and override the filename
     * 
     * @param resource path to the dat file
     * @return the initial IBDO
     */
    @ForOverride
    protected IBaseDataObject getInitialIbdo(final String resource) {
        return RegressionTestUtil.getInitialIbdoWithFormInFilename(new ClearDataBaseDataObject(), resource, kff);
    }

    /**
     * Allow the initial IBDO to be overridden before serialising to XML.
     * 
     * In the default case, we null out the data in the BDO which will force the data to be loaded from the .dat file
     * instead.
     * 
     * @param resource path to the dat file
     * @param initialIbdo to tweak
     */
    @ForOverride
    protected void tweakInitialIbdoBeforeSerialisation(final String resource, final IBaseDataObject initialIbdo) {
        if (initialIbdo instanceof ClearDataBaseDataObject) {
            ((ClearDataBaseDataObject) initialIbdo).clearData();
        } else {
            fail("Didn't get an expected type of IBaseDataObject");
        }
    }

    /**
     * Allow the generated IBDO to be overridden - for example, adding certain field values. Will modify the provided IBDO.
     * 
     * This is used in the simple case to set the current form for the final object to be taken from the file name. If the
     * test worked correctly no change will be made, but if there is a discrepancy this will be highlighted afterwards when
     * the diff takes place.
     * 
     * @param resource path to the dat file
     * @param finalIbdo the existing final BDO after it's been processed by a place
     */
    @ForOverride
    protected void tweakFinalIbdoBeforeSerialisation(final String resource, final IBaseDataObject finalIbdo) {
        RegressionTestUtil.tweakFinalIbdoWithFormInFilename(resource, finalIbdo);
    }

    /**
     * Allow the children generated by the place to be overridden before serialising to XML.
     * 
     * In the default case, do nothing.
     * 
     * @param resource path to the dat file
     * @param children to tweak
     */
    @ForOverride
    protected void tweakFinalResultsBeforeSerialisation(final String resource, final List<IBaseDataObject> children) {
        // No-op unless overridden
    }

    /**
     * Allows the log events generated by the place to be modified before serialising to XML.
     * 
     * In the default case, do nothing.
     * 
     * @param resource path to the dat file
     * @param simplifiedLogEvents to tweak
     */
    @ForOverride
    protected void tweakFinalLogEventsBeforeSerialisation(final String resource, final List<SimplifiedLogEvent> simplifiedLogEvents) {
        // No-op unless overridden
    }

    @Override
    @ForOverride
    protected String getInitialForm(final String resource) {
        return RegressionTestUtil.getInitialFormFromFilename(resource);
    }

    /**
     * This method returns the XML element decoders.
     * 
     * @return the XML element decoders.
     */
    protected ElementDecoders getDecoders() {
        return IBaseDataObjectXmlCodecs.DEFAULT_ELEMENT_DECODERS;
    }

    /**
     * This method returns the XML element encoders.
     * 
     * @return the XML element encoders.
     */
    protected ElementEncoders getEncoders() {
        return IBaseDataObjectXmlCodecs.SHA256_ELEMENT_ENCODERS;
    }

    /**
     * When the data is able to be retrieved from the XML (e.g. when getEncoders() returns the default encoders), then this
     * method should be empty. However, in this case getEncoders() is returning the sha256 encoders which means the original
     * data cannot be retrieved from the XML. Therefore, in order to test equivalence, all of the non-printable data in the
     * IBaseDataObjects needs to be converted to a sha256 hash. The full encoders can be used by overriding the
     * checkAnswersPreHook(...) to be empty and overriding getEncoders() to return the DEFAULT_ELEMENT_ENCODERS.
     */
    @Override
    protected void checkAnswersPreHook(final Document answers, final IBaseDataObject payload, final List<IBaseDataObject> attachments,
            final String tname) {

        if (getLogbackLoggerName() != null) {
            checkAnswersPreHookLogEvents(actualSimplifiedLogEvents);
        }

        if (!IBaseDataObjectXmlCodecs.SHA256_ELEMENT_ENCODERS.equals(getEncoders())) {
            return;
        }

        // touch up alternate views to match how their bytes would have encoded into the answer file
        for (Entry<String, byte[]> entry : new TreeMap<>(payload.getAlternateViews()).entrySet()) {
            Optional<String> viewSha256 = hashBytesIfNonPrintable(entry.getValue());
            viewSha256.ifPresent(s -> payload.addAlternateView(entry.getKey(), s.getBytes(StandardCharsets.ISO_8859_1)));
        }

        // touch up primary view if necessary
        Optional<String> payloadSha256 = hashBytesIfNonPrintable(payload.data());
        payloadSha256.ifPresent(s -> payload.setData(s.getBytes(StandardCharsets.UTF_8)));

        if (payload.getExtractedRecords() != null) {
            for (final IBaseDataObject extractedRecord : payload.getExtractedRecords()) {
                Optional<String> recordSha256 = hashBytesIfNonPrintable(extractedRecord.data());
                recordSha256.ifPresent(s -> extractedRecord.setData(s.getBytes(StandardCharsets.UTF_8)));
            }
        }

        if (attachments != null) {
            for (final IBaseDataObject attachment : attachments) {
                if (ByteUtil.hasNonPrintableValues(attachment.data())) {
                    Optional<String> attachmentSha256 = hashBytesIfNonPrintable(attachment.data());
                    attachmentSha256.ifPresent(s -> attachment.setData(s.getBytes(StandardCharsets.UTF_8)));
                }
            }
        }
    }

    /**
     * This method returns the logger name to capture log events from or null if log events are not to be checked.
     * 
     * @return the logger name to capture log events from or null (the default) if log events are not to be checked.
     */
    protected String getLogbackLoggerName() {
        return null;
    }

    /**
     * This method allows log events to be modified prior to checkAnswers being called.
     * 
     * In the default case, do nothing.
     * 
     * @param simplifiedLogEvents the log events to be tweaked.
     */
    protected void checkAnswersPreHookLogEvents(List<SimplifiedLogEvent> simplifiedLogEvents) {
        // No-op unless overridden
    }

    /**
     * Generates a SHA 256 hash of the provided bytes if they contain any non-printable characters
     * 
     * @param bytes the bytes to evaluate
     * @return a value optionally containing the generated hash
     */
    protected Optional<String> hashBytesIfNonPrintable(byte[] bytes) {
        if (ArrayUtils.isNotEmpty(bytes) && ByteUtil.hasNonPrintableValues(bytes)) {
            return Optional.ofNullable(ByteUtil.sha256Bytes(bytes));
        }

        return Optional.empty();
    }

    protected static class ClearDataBaseDataObject extends BaseDataObject {
        private static final long serialVersionUID = -8728006876784881020L;

        protected void clearData() {
            theData = null;
            seekableByteChannelFactory = null;
        }
    }

    @ParameterizedTest
    @MethodSource("data")
    @Override
    public void testExtractionPlace(final String resource) {
        logger.debug("Running {} test on resource {}", place.getClass().getName(), resource);

        if (generateAnswers()) {
            try {
                generateAnswerFiles(resource);
            } catch (final Exception e) {
                logger.error("Error running test {}", resource, e);
                fail("Unable to generate answer file", e);
            }
        }

        // Run the normal extraction/regression tests
        super.testExtractionPlace(resource);
    }

    /**
     * Actually generate the answer file for a given resource
     * 
     * Takes initial form and final forms from the filename
     * 
     * @param resource to generate against
     * @throws Exception if an error occurs during processing
     */
    protected void generateAnswerFiles(final String resource) throws Exception {
        // Get the data and create a channel factory to it
        final IBaseDataObject initialIbdo = getInitialIbdo(resource);
        // Clone the BDO to create an 'after' copy
        final IBaseDataObject finalIbdo = IBaseDataObjectHelper.clone(initialIbdo, true);
        // Actually process the BDO and keep the children
        final List<IBaseDataObject> finalResults;
        final List<SimplifiedLogEvent> finalLogEvents;
        if (getLogbackLoggerName() == null) {
            finalResults = place.agentProcessHeavyDuty(finalIbdo);
            finalLogEvents = new ArrayList<>();
        } else {
            try (LogbackTester logbackTester = new LogbackTester(getLogbackLoggerName())) {
                finalResults = place.agentProcessHeavyDuty(finalIbdo);
                finalLogEvents = logbackTester.getSimplifiedLogEvents();
            }
        }

        // Allow overriding things before serialising to XML
        tweakInitialIbdoBeforeSerialisation(resource, initialIbdo);
        tweakFinalIbdoBeforeSerialisation(resource, finalIbdo);
        tweakFinalResultsBeforeSerialisation(resource, finalResults);
        tweakFinalLogEventsBeforeSerialisation(resource, finalLogEvents);

        // Generate the full XML (setup & answers from before & after)
        if (super.answerFileClassRef == null) {
            RegressionTestUtil.writeAnswerXml(resource, initialIbdo, finalIbdo, finalResults, finalLogEvents, getEncoders());
        } else {
            RegressionTestUtil.writeAnswerXml(resource, initialIbdo, finalIbdo, finalResults, finalLogEvents, getEncoders(),
                    super.answerFileClassRef);
        }
    }

    @Override
    protected List<IBaseDataObject> processHeavyDutyHook(IServiceProviderPlace place, IBaseDataObject payload)
            throws Exception {
        if (getLogbackLoggerName() == null) {
            actualSimplifiedLogEvents = new ArrayList<>();

            return super.processHeavyDutyHook(place, payload);
        } else {
            try (LogbackTester logbackTester = new LogbackTester(getLogbackLoggerName())) {
                final List<IBaseDataObject> attachments = super.processHeavyDutyHook(place, payload);

                actualSimplifiedLogEvents = logbackTester.getSimplifiedLogEvents();

                return attachments;
            }
        }
    }

    @Override
    protected Document getAnswerDocumentFor(final String resource) {
        // If generating answers, get the src version, otherwise get the normal XML file
        return generateAnswers() ? RegressionTestUtil.getAnswerDocumentFor(resource) : super.getAnswerDocumentFor(resource);
    }

    @Override
    protected void setupPayload(final IBaseDataObject payload, final Document answers) {
        RegressionTestUtil.setupPayload(payload, answers, getDecoders());
    }

    @Override
    protected void checkAnswers(final Document answers, final IBaseDataObject payload,
            final List<IBaseDataObject> attachments, final String tname) {
        RegressionTestUtil.checkAnswers(answers, payload, actualSimplifiedLogEvents, attachments, place.getClass().getName(), getDecoders());
    }
}
