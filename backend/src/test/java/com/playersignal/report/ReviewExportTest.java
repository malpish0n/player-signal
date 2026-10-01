package com.playersignal.report;
import java.io.ByteArrayOutputStream;
import com.playersignal.shared.ApiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ReviewExportTest {
 @Test void escapesCsvAndNeutralizesFormulaCellsWithoutChangingOrdinaryQuotesOrUnicode() {
  assertThat(ReviewExport.row("Zażółć, \"test\"\nnext", "=1+1", "  @SUM(A1)", "\t=cmd", "-1", "plain"))
   .isEqualTo("\"Zażółć, \"\"test\"\"\nnext\",\"'=1+1\",\"'  @SUM(A1)\",\"'\t=cmd\",\"'-1\",\"plain\"\r\n");
 }
 @Test void byteLimitAccountsForUtf8AndNeverAppendsPartialRows() {
  var output=new ByteArrayOutputStream();output.writeBytes(new byte[ReviewExport.MAX_BYTES-1]);
  assertThatThrownBy(()->ReviewExport.append(output,"ą")).isInstanceOf(ApiException.class);
  assertThat(output.size()).isEqualTo(ReviewExport.MAX_BYTES-1);
 }
}
