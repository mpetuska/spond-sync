package cli.config

import cli.SyncWorker
import me.tatarka.inject.annotations.Inject

@Inject class ClubApp(val syncWorker: SyncWorker)
