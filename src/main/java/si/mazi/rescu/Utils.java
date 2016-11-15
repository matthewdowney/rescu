/*
 * Copyright (C) 2015 Matija Mazi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package si.mazi.rescu;

import java.util.Arrays;

public final class Utils {

    private Utils() throws InstantiationException {
        throw new InstantiationException("This class is not for instantiation");
    }

    public static String clip(String str, int startChars) {
        return str.length() <= startChars ? str : str.substring(0, startChars) + "...";
    }

    public static <T> T[] arrayConcat(T[] a, T[] b) {
      T[] concatted = Arrays.copyOf(a, a.length + b.length);
      for (int i = 0; i < b.length; ++i) {
        concatted[a.length + i] = b[i];
      }
      return concatted;
    }

}
