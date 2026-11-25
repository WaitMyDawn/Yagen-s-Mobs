"""Run the test-only census mod on an isolated copy of an installed NeoForge dedicated server."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server", required=True, type=Path, help="Installed server with an accepted eula.txt")
    parser.add_argument("--java", required=True, type=Path)
    parser.add_argument("--mob-jar", required=True, type=Path)
    parser.add_argument("--gecko-jar", required=True, type=Path)
    parser.add_argument("--probe-jar", required=True, type=Path)
    parser.add_argument("--work", required=True, type=Path, help="A new directory for one seed")
    parser.add_argument("--seed", required=True, type=int)
    parser.add_argument("--property", action="append", default=[], help="Test property such as ticks=24000 or from=15")
    parser.add_argument("--config", type=Path, help="Optional server configuration for a rules-only control")
    args = parser.parse_args()
    eula = args.server/"eula.txt"
    if "eula=true" not in eula.read_text().splitlines():
        parser.error("The source server must already have an accepted EULA.")
    argfile = "win_args.txt" if os.name == "nt" else "unix_args.txt"
    candidates = list((args.server/"libraries/net/neoforged/neoforge").glob("*/"+argfile))
    if len(candidates) != 1:
        parser.error("Expected one installed NeoForge version in the source server.")
    if args.work.exists():
        parser.error("Each test run requires a new world directory.")
    args.work.mkdir(parents=True)
    def link_or_copy(src, dst):
        try:
            os.link(src, dst)
        except OSError:
            shutil.copy2(src, dst)
    shutil.copytree(args.server/"libraries", args.work/"libraries", copy_function=link_or_copy)
    shutil.copy2(eula, args.work)
    mods = args.work/"mods"
    mods.mkdir()
    for jar in (args.mob_jar, args.gecko_jar, args.probe_jar):
        shutil.copy2(jar, mods)
    if args.config:
        target = args.work/"config/yagens_mobs"
        target.mkdir(parents=True)
        shutil.copy2(args.config, target/"yagens_mobs-server.toml")
    (args.work/"server.properties").write_text(
        f"level-seed={args.seed}\nserver-ip=127.0.0.1\nserver-port=0\nonline-mode=false\n"
        "view-distance=3\nsimulation-distance=3\nmax-tick-time=-1\ndifficulty=normal\nspawn-protection=0\n")
    command = [str(args.java.resolve()), "-Xms600m", "-Xmx2300m", "-XX:ActiveProcessorCount=5",
               *("-Dsurvey."+value for value in args.property),
               "@"+str(candidates[0].relative_to(args.server)), "nogui"]
    with (args.work/"survey.log").open("w", encoding="utf-8") as log:
        with subprocess.Popen(command, cwd=args.work, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT) as server:
            print(f"Survey seed {args.seed}, PID {server.pid}", flush=True)
            try:
                code = server.wait()
            except KeyboardInterrupt:
                server.communicate(b"stop\n")
                raise
    # The dedicated server can exit with status zero after writing a crash report.
    finished = (args.work/"survey.log").read_text(encoding="utf-8", errors="replace")
    if code or not any(marker in finished for marker in ("SURVEY_COMPLETE ", "SURVEY_RULES_PASS ")):
        raise SystemExit(f"Survey did not complete. See {args.work/'survey.log'}")


if __name__ == "__main__":
    main()

