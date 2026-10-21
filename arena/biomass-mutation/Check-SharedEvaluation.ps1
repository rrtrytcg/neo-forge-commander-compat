$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$baseline='8578241e2caf3db492b9379e02c03f7196566a19'
$source=(& git -C $repo show "${baseline}:forge-ai/src/main/java/forge/ai/MirrorEntityAi.java") -join "`n"
if($LASTEXITCODE -ne 0) { throw 'Committed Mirror Entity baseline unavailable' }
$expected=$source.Replace('import java.util.Set;',"import java.util.Set;`nimport java.util.function.Predicate;").Replace('MirrorEntityAi','CombatBaseStatsAi').Replace('Bounded combat-only activation policy; all execution remains native.','Shared bounded combat-only base-P/T evaluation; all execution remains native.')
$start=$expected.IndexOf('    public static boolean uses(')
$end=$expected.IndexOf('    private static AiAbilityDecision hold(', $start)
$expected=$expected.Remove($start,$end-$start).Replace('consider(final Player ai, final SpellAbility sa)','consider(final Player ai, final SpellAbility sa, final Predicate<SpellAbility> matches)').Replace('if (uses(pending)','if (matches.test(pending)')
$actual=(Get-Content (Join-Path $repo 'forge-ai/src/main/java/forge/ai/CombatBaseStatsAi.java') -Raw).Replace("`r`n","`n")
if($actual.TrimEnd() -cne $expected.TrimEnd()) { throw 'Shared evaluation differs from the original Mirror Entity algorithm beyond class/predicate extraction' }
Write-Output 'Shared combat evaluation matches committed Mirror Entity baseline after mechanical extraction'
