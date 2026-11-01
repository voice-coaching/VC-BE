package org.example.voice.common.logging;

import org.springframework.boot.logging.StackTracePrinter;
import java.io.IOException;

/** Exception messages can contain request bodies, credentials or SDK responses. Print locations only. */
public final class SafeStackTracePrinter implements StackTracePrinter {
    @Override public void printStackTrace(Throwable error, Appendable out) throws IOException {
        for (int cause=0; error!=null && cause<3; cause++,error=error.getCause()) {
            out.append(error.getClass().getName()).append('\n');
            var frames=error.getStackTrace();
            for (int i=0;i<Math.min(20,frames.length);i++) {
                var frame=frames[i];
                out.append("  at ").append(frame.getClassName()).append('.').append(frame.getMethodName())
                        .append(':').append(Integer.toString(frame.getLineNumber())).append('\n');
            }
        }
    }
}
