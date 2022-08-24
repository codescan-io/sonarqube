import React from 'react';
import { getQueueStatus } from '../../../api/codescan';

export default class StatusMonitor extends React.PureComponent {
    statuses: any;
    callback: any;
    timer: any;
    ids: any = [];

    start = (queues: any, callbacks: any) => {
        this.stop();
        this.statuses = {};
        
        // eslint-disable-next-line guard-for-in
        for (const i in queues) {
            const q = queues[i];
            if ( !q.isDone ){
                this.statuses[q.id] = q.status;
                this.ids.push(q.id);
            }
        }

        if ( this.ids.length > 0 ){
            this.callback = callbacks;
            this.timer = setIntervalSmart(this.onTimer, 5000);
        }
    }

    onTimer = () => {
        // eslint-disable-next-line promise/catch-or-return
        getQueueStatus(this.ids).then(resp => {
            for ( const i in resp ){
                if ( resp[i] !== this.statuses[i] ){
                    if (this.callback){
                        this.callback();
                    }
                    this.stop();
                    break;
                }
            }
        })
    }

    stop = () => {
        if (this.timer){
            this.timer.stop();
            this.timer = null;
            this.callback = null;
            this.ids = null;
            this.statuses = null;
        }
    }

}


class SmartInterval {
    timer: any;
    isForeground: boolean;
    foregroundCheckInterval: any;
    foregroundInterval: any;
    callback: any;

    constructor(callback: any, foregroundInterval: any, foregroundCheckInterval: any) {
      this.onCallback = this.onCallback.bind(this);
      this.callback = callback;
      this.foregroundInterval = foregroundInterval;
      this.foregroundCheckInterval = typeof(foregroundCheckInterval) == 'undefined' ? 500 : foregroundCheckInterval;
      this.timer = setInterval(this.onCallback, foregroundInterval);
      this.isForeground = true;
    }

    onCallback(){
      if ( isWindowHidden() ){
        if ( this.isForeground ){
          this.isForeground = false;
          clearInterval(this.timer);
          this.timer = setInterval(this.onCallback, this.foregroundCheckInterval);
        }
      }else{
        if ( !this.isForeground ){
          this.isForeground = true;
          clearInterval(this.timer);
          this.timer = setInterval(this.onCallback, this.foregroundInterval);
        }
        this.callback();
      }
    }

    stop(){
      clearInterval(this.timer);
    }
  }

  /**
   * setInterval replacement that takes into account whether the window is visible
   * callback is called every foregroundInterval, unless the window is hidden (see isWindowHidden)
   * in which case it is not called. however, the timer still runs (in fact runs every 200ms by default)
   * when in the background so that the callback is called almost immediately if the window becomes visible
   *
   * @param {function} callback
   * @param {int} foregroundInterval
   */
function setIntervalSmart(callback: any, foregroundInterval: any, foregroundCheckInterval?: any){
    return new SmartInterval(callback, foregroundInterval, foregroundCheckInterval);
}

function isWindowHidden()
  {
    const doc: any = document || {};
    return !!(doc.hidden || doc.msHidden || doc.webkitHidden || doc.mozHidden);
  }