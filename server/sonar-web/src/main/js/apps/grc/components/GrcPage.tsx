import React, {useEffect} from "react";
import "../styles.css";
import {setGrcUi} from "../../../store/appState";
import {connect} from "react-redux";

interface Props {
  children: React.ReactNode;
  setGrcUi: (grc: boolean) => void;
}

function GrcPage({setGrcUi, children}: Props) {

  useEffect(() => {
    // Set 'grc' state variable to true when component is mounted.
    setGrcUi(true);

    // Set 'grc' state variable to false when component is unmounted.
    return () => {
      setGrcUi(false);
    };
  }, []);


  return (
      <div className="grc-container">
        {children}
      </div>
  );
}

const mapStateToProps = () => ({
});

const mapDispatchToProps = {
  setGrcUi
};

export default connect(mapStateToProps, mapDispatchToProps)(GrcPage);