/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Yegor Bugayenko
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.yegor256.goproxy;

import com.jcabi.log.Logger;
import com.yegor256.asto.Storage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.cactoos.io.OutputTo;
import org.cactoos.io.ResourceOf;
import org.cactoos.io.TeeInput;
import org.cactoos.scalar.LengthOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Integration case for {@link Goproxy}.
 *
 * @author Yegor Bugayenko (yegor256@gmail.com)
 * @version $Id$
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public final class GoproxyITCase {

    /**
     * Temp folder for all tests.
     */
    @Rule
    @SuppressWarnings("PMD.BeanMembersShouldSerialize")
    public TemporaryFolder folder = new TemporaryFolder();

    /**
     * Make sure Go is here.
     * @throws Exception If fails
     */
    @Before
    public void goExists() throws Exception {
        Assume.assumeThat(
            "Go is NOT present at the build machine",
            new ProcessBuilder()
                .command("which", "go")
                .start()
                .waitFor(),
            Matchers.equalTo(0)
        );
    }

    /**
     * RPM works.
     * @throws Exception If some problem inside
     */
    @Test
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public void savesAndLoads() throws Exception {
        final Path repo = this.folder.newFolder("repo").toPath();
        for (final String file
            : new String[] {"bar.go", "go.mod", "texts/test.txt"}) {
            new LengthOf(
                new TeeInput(
                    new ResourceOf(String.format("bar/%s", file)),
                    new OutputTo(Paths.get(repo.toString(), "foo", "bar", file))
                )
            ).intValue();
        }
        final Storage storage = new Storage.Simple(repo);
        final Goproxy goproxy = new Goproxy(storage);
        goproxy.update("example.com/foo/bar", "0.0.123");
        goproxy.update("example.com/foo/bar", "0.0.124");
        final Path stdout = this.folder.newFile("stdout.txt").toPath();
        final Path home = this.folder.newFolder("home").toPath();
        Files.write(
            Paths.get(home.toString(), "go.mod"),
            String.join(
                "\n",
                "module example.com/foo/xxx",
                "require example.com/foo/bar v0.0.124",
                "go 1.11"
            ).getBytes()
        );
        Files.write(
            Paths.get(home.toString(), "test.go"),
            String.join(
                "\n",
                "package main",
                "import \"fmt\"",
                "import \"example.com/foo/bar\"",
                "func main() {",
                    "fmt.Println(\"Hey, you!\")",
                    "bar.SayHello()",
                "}"
            ).getBytes()
        );
        new ProcessBuilder()
            .directory(home.toFile())
            .command(
                "/bin/bash",
                "-c",
                String.join(
                    "\n",
                    "set -e",
                    "set -x",
                    "export GOSUMDB=off",
                    String.format("export GOPATH=%s/gopath", repo),
                    String.format("export GOPROXY=file://%s", repo),
                    "go install -v",
                    "go run test.go"
                )
            )
            .redirectOutput(stdout.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor();
        final String log = new String(Files.readAllBytes(stdout));
        Logger.debug(this, "Full stdout/stderr:\n%s", log);
        MatcherAssert.assertThat(
            log,
            Matchers.allOf(
                Matchers.containsString(
                    "go: downloading example.com/foo/bar v0.0.124"
                ),
                Matchers.containsString("Hey, you!"),
                Matchers.containsString("Works!!!")
            )
        );
    }

}
