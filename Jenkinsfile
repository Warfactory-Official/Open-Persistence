@Library('wf-ci') _

def libs = 'versions/1.12.2/build/libs versions/1.20.1/fabric/build/libs versions/1.20.1/forge/build/libs ' +
    'versions/1.21.1/fabric/build/libs versions/1.21.1/neoforge/build/libs ' +
    'versions/26.1/fabric/build/libs versions/26.1/neoforge/build/libs'

wfBuild(
    repo: 'Open-Persistence',
    jdk: '25',
    branches: ['main', 'master'],
    build: '--no-daemon -PmodVersion=1.0.0-SNAPSHOT :1.12.2:build :1.20.1:fabric:build :1.20.1:forge:build ' +
        ':1.21.1:fabric:build :1.21.1:neoforge:build :26.1:fabric:build :26.1:neoforge:build',
    archive: 'versions/**/build/libs/*.jar',
    latest: [
        tag: 'snapshots',
        title: 'Snapshots',
        notes: 'Rolling snapshot build of Open Persistence for Minecraft 1.12.2, 1.20.1, 1.21.1 and 26.1 from commit ${sha}.',
        filesCmd: "find ${libs} -maxdepth 1 -name '*.jar' ! -name '*-dev.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' | sort",
        prerelease: true,
    ],
    tagRolling: [
        tag: 'wf', jdk: '25', build: ':1.20.1:forge:build -PmodVersion=1.1.0 --no-daemon',
        stage: 'cp "$(find versions/1.20.1/forge/build/libs -name \'*.jar\' ! -name \'*-sources*\' ! -name \'*-dev*\' | head -1)" openpersistence.jar',
        filesCmd: 'echo openpersistence.jar',
    ],
)
