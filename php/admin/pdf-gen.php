<?php

require_once 'pdfGraph.php';

define("STEP", 1);

define("HOUR", 3600);
define("DAY", 24 * HOUR);

global $g_report;
global $g_title;
global $g_is_watchdog;
global $g_is_snapshot;
global $profile_time;
global $period;

$g_report = get_param($g_report, "report", "Snapshot");
$g_title = get_param($g_title, "title", $g_report);

if ($g_is_snapshot || $_REQUEST["snapshot"]) {
  $snapshot = $g_mbean_server->lookup("resin:type=SnapshotService");

  if ($snapshot) {
    $snapshot->snapshotJmx();
    $snapshot->snapshotHeap();
    $snapshot->snapshotHealth();
    $snapshot->snapshotThreadDump();

    if ($profile_time || $_REQUEST["profile_time"]) {
      
      if (! $profile_time)
        $profile_time = $_REQUEST["profile_time"];
      
      if (! $profile_depth)
        $profile_depth = $_REQUEST["profile_depth"];
      
      if (! $profile_tick)
        $profile_tick = $_REQUEST["profile_tick"];

      if (! $profile_depth)
        $profile_depth = 16;
      
      if (! $profile_tick)
        $profile_tick = 100;

      $snapshot->snapshotProfile(($profile_time*1000), $profile_tick, $profile_depth);
    }

    sleep(2);
  }
}

initPDF();

$mPage = getMeterGraphPage($g_report);
if (! $mPage) {
  $mPage = getMeterGraphPage("Snapshot");
}

$title = $g_title;

if (! $title && $mPage)
  $title = $mPage->name;

if (! $title)
  $title = $pdf_name;

if (! $title)
  $title = "Snapshot";

$g_canvas->header_center_text = "$g_title Report";

if (! $period) {
  $period = $_REQUEST['period'] ? (int) $_REQUEST['period'] : ($mPage->period/1000);
}

if ($period < HOUR) {
  $majorTicks = HOUR / 6;
}
elseif ($period >= HOUR && $period < 3 * HOUR) {
  $majorTicks = HOUR / 2;
}
elseif ($period >= 3 * HOUR && $period < 6 * HOUR) {
  $majorTicks = HOUR;
}
elseif ($period >= 6 * HOUR && $period < 12 * HOUR) {
  $majorTicks = 2 * HOUR;
}
elseif ($period >= 12 * HOUR && $period < 24 * HOUR) {
  $majorTicks = 4 * HOUR;
}
elseif ($period >= 24 * HOUR && $period <= 48 * HOUR) {
  $majorTicks = 6 * HOUR;
}
else {
  $majorTicks = 24 * HOUR;
}

$majorTicks = $majorTicks * 1000;
$minorTicks = $majorTicks/4;

$page = 0;

$g_canvas->header_left_text = $g_label;

$time = $_REQUEST["time"];

if (! $time) {
  if ($g_is_watchdog) {
    $time = $g_server->StartTime->getTime() / 1000;
  }
  else {
    $time = time() + 5;
  }
}  

$g_end = $time;

if (2 * DAY <= $period) {
  $tz = date_offset_get(new DateTime);

  $ticks_sec = $majorTicks / 1000;

  $g_end = ceil(($g_end + $tz) / $ticks_sec) * $ticks_sec - $tz;
}

$g_start = $g_end - $period;

$g_canvas->footer_left_text = date("Y-m-d H:i", $g_end);
$g_canvas->footer_right_text = date("Y-m-d H:i", $g_end);

$g_canvas->writeSection("$g_title Report", false);

$col1 = 85;
$col2 = 300;

$g_canvas->writeTextColumn($col1, 'r', "Report Generated:");
$g_canvas->writeTextColumn($col2, 'l', date("Y-m-d H:i", time()));
$g_canvas->newLine();

$g_canvas->writeTextColumn($col1, 'r', "Snapshot Taken:");
$g_canvas->writeTextColumn($col2, 'l', date("Y-m-d H:i", $g_end));
$g_canvas->newLine();

$g_canvas->writeTextColumn($col1, 'r', "Data Range:");
$g_canvas->writeTextColumn($col2, 'l', date("Y-m-d H:i", $g_start) . " through " . date("Y-m-d H:i", $g_end));
$g_canvas->newLine();

if ($mPage->isSummary()) {
  admin_pdf_summary();
}

admin_pdf_health($g_canvas);

draw_cluster_graphs($mPage);

draw_graphs($mPage, $g_canvas);

if ($mPage->isHeapDump()) {
  admin_pdf_heap_dump();
}
 
if ($mPage->isProfile()) {
  admin_pdf_profile();
}

if ($mPage->isThreadDump()) {
  admin_pdf_thread_dump();
}

if ($mPage->isLog()) {
  admin_pdf_draw_log();
}

if ($mPage->isJmxDump()) {
  admin_pdf_jmx_dump();
}

$g_canvas->end();

$filename = "$g_title" . "_" . date("Ymd_Hi", $g_end) . ".pdf";

$g_canvas->writeSelfHttp($filename);

// needed for PdfReport health action
return "ok";

?>
