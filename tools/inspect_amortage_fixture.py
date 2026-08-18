import json
from pathlib import Path

release = json.loads(Path('app/src/test/resources/fixtures/release_amortage.json').read_text())
work = json.loads(Path('app/src/test/resources/fixtures/work_earthquake.json').read_text())
track = next(track for medium in release['media'] for track in medium['tracks'] if track['title'] == 'earthquake')
recording = track['recording']
relations = recording.get('relations', [])
print('release', release['id'], release['title'])
print('track', track['title'], 'recording', recording['id'], 'recording_relations', len(relations))
for relation in relations:
    target = relation.get('artist') or relation.get('label') or relation.get('work') or relation.get('url') or {}
    print(relation.get('type'), relation.get('target-type'), target.get('name') or target.get('title'), relation.get('attributes', []), relation.get('attribute-values', {}), relation.get('begin'), relation.get('end'))
print('work', work['id'], work['title'], 'relations', len(work.get('relations', [])))
print('work writers', [r.get('artist', {}).get('name') for r in work.get('relations', []) if r.get('type') == 'writer'])
