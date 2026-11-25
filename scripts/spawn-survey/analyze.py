"""Summarize completed NeoForge field stations and the first 16x16 population window per habitat."""
import argparse
import csv
from collections import Counter, defaultdict
from pathlib import Path


def rows(path):
    with path.open(encoding="utf-8", newline="") as stream:
        # The probe appends one TSV record per line. A live read can catch the writer partway through a record.
        yield from csv.reader((line for line in stream if line.endswith("\n")), delimiter="\t")


def write(path, header, data):
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(header)
        writer.writerows(data)


def survey(folders, output):
    output.mkdir(parents=True, exist_ok=True)
    field = Counter()
    timings = {}
    stations = {}
    candidates = Counter()
    terrain = Counter()
    population_chunks = Counter()
    controls = []
    capacity = []
    residents = []
    sampled_chunks = set()
    completed_seeds = set()
    for folder in folders:
        seed = next(line.split("=", 1)[1] for line in (folder / "server.properties").read_text().splitlines() if line.startswith("level-seed="))
        starts = {}
        done = {}
        for row in rows(folder / "sites.tsv"):
            if row[0] == "start":
                _, site, habitat, rep, dimension, *coords = row
                starts[site] = habitat, int(rep), dimension, *map(int, coords)
            elif row[0] == "done":
                # The probe stores the final zero-based tick sampled before disabling the listener.
                done[row[1]] = int(row[2]) + 1, int(row[3])
        for site, (ticks, cap_ticks) in done.items():
            habitat, rep, dimension, x, y, z, cx, cz = starts[site]
            key = seed, habitat, rep
            if key in stations:
                raise ValueError(f"Duplicate observation {key}")
            stations[key] = dimension, x, y, z, cx, cz, ticks, cap_ticks
        for site, tick, biome, category, entity, *_ in rows(folder / "samples.tsv"):
            if site in done:
                habitat, rep, *_ = starts[site]
                key = seed, habitat, rep
                field[*key, biome, category, entity] += 1
                first = (*key, entity)
                timings[first] = min(timings.get(first, int(tick)), int(tick))
        for site, stage, category, entity, biome, value in rows(folder / "counts.tsv"):
            if site in done:
                habitat, rep, *_ = starts[site]
                candidates[seed, habitat, rep, biome, category, entity, stage] += int(value)
        if (folder / "residents.tsv").is_file():
            for site, entity, value in rows(folder / "residents.tsv"):
                if site in done:
                    habitat, rep, *_ = starts[site]
                    residents.append((seed, habitat, rep, entity, int(value)))
        if (folder / "capacity.tsv").is_file():
            for site, habitat, category, ticks, full, chunk_ticks in rows(folder / "capacity.tsv"):
                if site in done:
                    # The launcher fixes the view and simulation distances at three chunks.
                    if int(chunk_ticks) < 49 * done[site][0]:
                        raise ValueError(f"Station {seed}/{habitat}/{site} has incomplete spawning chunk exposure")
                    capacity.append((seed, habitat, starts[site][1], category, int(ticks), int(full), int(chunk_ticks)))
        windows = [(*starts[site][:3], *starts[site][-2:]) for site in done if starts[site][1] == 0]
        for habitat, _, dimension, cx, cz in windows:
            window = {(seed, dimension, x, z) for x in range(cx - 8, cx + 8) for z in range(cz - 8, cz + 8)}
            if sampled_chunks.intersection(window):
                raise ValueError(f"Overlapping population window at {seed} {habitat}")
            sampled_chunks.update(window)
        by_uuid = set()
        for dim, x, y, z, biome, category, entity, uuid in rows(folder / "population.tsv"):
            if uuid in by_uuid:
                continue
            by_uuid.add(uuid)
            cx, cz = int(x)//16, int(z)//16
            for habitat, rep, dimension, center_x, center_z in windows:
                if dim == dimension and -8 <= cx-center_x < 8 and -8 <= cz-center_z < 8:
                    terrain[seed, habitat, rep, biome, category, entity] += 1
                    population_chunks[seed, habitat, dimension, cx, cz, entity] += 1
        if seed not in completed_seeds:
            controls.extend((seed, *row) for row in rows(folder / "controls.tsv"))
            completed_seeds.add(seed)
    prefix = ["seed", "station_habitat", "rep"]
    accepted = Counter({key[:-1]: value for key, value in candidates.items() if key[-1] == "spawn"})
    if accepted != field:
        raise ValueError("Natural arrivals and per-station spawn event counters disagree")
    write(output/"stations.csv", prefix+["dimension","x","y","z","chunk_x","chunk_z","observation_ticks","sum_cap_denominator_chunks"], ((*k,*v) for k,v in sorted(stations.items())))
    write(output/"controls.csv", ["seed","difficulty","lighting","actual_light","floor","species","passes_out_of_100"], controls)
    write(output/"population_chunks.csv", ["seed", "station_habitat", "dimension", "chunk_x", "chunk_z", "species", "count"],
          ((*k, n) for k, n in sorted(population_chunks.items())))
    detail = [(phase,*key,count) for phase,counts in (("AFK",field),("WORLDGEN",terrain)) for key,count in sorted(counts.items())]
    write(output/"species.csv", ["phase",*prefix,"spawn_biome","category","species","count"],detail)
    write(output/"stages.csv", prefix+["candidate_biome","category","species","stage","count"], ((*k,n) for k,n in sorted(candidates.items())))
    write(output/"first_seen.csv", prefix+["species","first_tick"], ((*k,v) for k,v in sorted(timings.items())))
    write(output/"capacity.csv", prefix+["category","ticks","global_cap_full_ticks","eligible_chunk_ticks"], sorted(capacity))
    if residents:
        write(output/"residents.csv", prefix+["species", "living_at_end"], sorted(residents))
    by_phase=Counter()
    by_pool=Counter()
    by_species=Counter()
    for phase,seed,habitat,rep,biome,category,entity,count in detail:
        by_phase[phase]+=count
        by_pool[phase,category]+=count
        by_species[phase,category,entity]+=count
    summary=[(phase,category,entity,count,by_pool[phase,category],by_phase[phase],round(100*count/by_pool[phase,category],4),round(100*count/by_phase[phase],4)) for (phase,category,entity),count in sorted(by_species.items())]
    write(output/"summary.csv", ["phase","category","species","count","pool_total","all_total","percent_of_pool","percent_of_all"],summary)
    for row in summary:
        if row[2].startswith("yagens_mobs:"): print(*row)
    print(f"{len(stations)} stations, {sum(v[-2] for v in stations.values())} ticks; {len(sampled_chunks)} window-chunks")


if __name__ == "__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folders", type=Path, nargs="+")
    parser.add_argument("--output", type=Path, required=True)
    args=parser.parse_args()
    survey(args.folders,args.output)

