import * as React from "react";

interface Props{

}

interface State{

}

export default class DashboardApp extends React.PureComponent<Props, State> {
    mounted = false;
  
    state: State = {
      loading: true,
    };
  
    componentDidMount() {
    }
  
    componentWillUnmount() {
    }
  
    render() {
      return (<span>Dashboard Comming soon!!!</span>)
    }
  }